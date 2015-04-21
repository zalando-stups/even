(ns org.zalando.stups.even.api
  (:require
    [org.zalando.stups.friboo.system.http :refer [def-http-component]]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [org.zalando.stups.even.pubkey-provider.ldap :refer [get-public-key ldap-auth? get-networks]]
    [org.zalando.stups.even.ssh :refer [execute-ssh]]
    [clojure.data.codec.base64 :as b64]
    [ring.util.http-response :as http]
    [ring.util.response :as ring]
    [clj-dns.core :as dns]
    [org.zalando.stups.even.net :refer [network-matches?]])
  (:import [clojure.lang ExceptionInfo]
           [org.apache.commons.net.util SubnetUtils]))

(def username-pattern
  "A valid POSIX user name (e.g. 'jdoe')"
  #"^[a-z][a-z0-9-]{0,31}$")
(def hostname-pattern
  "A valid FQDN or IP address"
  #"^[a-z0-9.-]{0,255}$")

(defn matches-username-pattern [s] (re-matches username-pattern s))
(defn matches-hostname-pattern [s] (re-matches hostname-pattern s))
(defn non-empty [s] (not (clojure.string/blank? s)))

(s/defschema AccessRequest
  {(s/optional-key :username)    (s/both String (s/pred matches-username-pattern))
   :hostname                     (s/both String (s/pred matches-hostname-pattern))
   :reason                       (s/both String (s/pred non-empty))
   (s/optional-key :remote-host) (s/both String (s/pred matches-hostname-pattern))
   })

(def-http-component API "api/even-api.yaml" [ldap ssh])

(def default-http-configuration {:http-port 8080})

(defn serve-public-key [{:keys [name]} request ldap _]
  (if (re-matches username-pattern name)
    (if-let [ssh-key (get-public-key name ldap)]
      (-> (ring/response ssh-key)
          (ring/header "Content-Type" "text/plain"))
      (http/not-found "User not found"))
    (http/bad-request "Invalid user name")))

(defn ensure-username [auth {:keys [username] :as req}]
  (assoc req :username (or username (:username auth))))

(defn parse-authorization
  "Parse HTTP Basic Authorization header"
  [authorization]
  (-> authorization
      (clojure.string/replace-first "Basic " "")
      .getBytes
      b64/decode
      String.
      (clojure.string/split #":" 2)
      (#(zipmap [:username :password] %))))

(defn extract-auth
  "Extract authorization from basic auth header"
  [req]
  (if-let [auth-value (get-in req [:headers "authorization"])]
    (parse-authorization auth-value)))

(defn request-access-with-auth
  "Request server access with provided auth credentials"
  [auth {:keys [hostname username remote-host reason]} ldap ssh]

  (log/info "Requesting access to " username "@" hostname ", remote-host=" remote-host ", reason=" reason)
  (if (ldap-auth? auth ldap)
    (let [ip (dns/to-inet-address hostname)
          networks (get-networks (:username auth) ldap)
          matching-networks (filter #(network-matches? % ip) networks)]
      (if (empty? matching-networks)
        (http/forbidden (str "Forbidden. Host " ip " is not in one of the allowed networks: " (print-str networks)))
        (let [result (execute-ssh hostname (str "grant-ssh-access --remote-host=" remote-host " " username) ssh)]
          (if (zero? (:exit result))
            (http/ok (str "Access to host " ip " for user " username " was granted."))
            (http/bad-request (str "SSH command failed: " (or (:err result) (:out result))))))))
    (http/forbidden "Authentication failed")))

(defn validate-request [request]
  (try (s/validate AccessRequest request)
       (catch ExceptionInfo e
         (throw (ex-info (str "Invalid request: " (.getMessage e)) {:http-code 400})))))

(defn request-access [{:keys [request]} ring-request ldap ssh]
  (if-let [auth (extract-auth ring-request)]
    (request-access-with-auth auth (ensure-username auth (validate-request request)) ldap ssh)
    (http/unauthorized "Unauthorized. Please authenticate with username and password.")))






