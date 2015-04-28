(ns org.zalando.stups.even.api
  (:require
    [org.zalando.stups.friboo.system.http :refer [def-http-component]]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [bugsbio.squirrel :as sq]
    [org.zalando.stups.even.pubkey-provider.ldap :refer [get-public-key ldap-auth? get-networks]]
    [org.zalando.stups.even.ssh :refer [execute-ssh]]
    [org.zalando.stups.even.sql :as sql]
    [clojure.data.codec.base64 :as b64]
    [ring.util.http-response :as http]
    [ring.util.response :as ring]
    [org.zalando.stups.friboo.ring :as fring]
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
(defn valid-lifetime [i] (and (pos? i) (<= i 525600)))

(s/defschema AccessRequest
  {(s/optional-key :username)         (s/both String (s/pred matches-username-pattern))
   :hostname                          (s/both String (s/pred matches-hostname-pattern))
   :reason                            (s/both String (s/pred non-empty))
   (s/optional-key :remote-host)      (s/both String (s/pred matches-hostname-pattern))
   (s/optional-key :lifetime_minutes) (s/both s/Int (s/pred valid-lifetime))
   })

(def-http-component API "api/even-api.yaml" [ldap ssh db])

(def default-http-configuration {:http-port 8080})

(def empty-access-request {:username nil :hostname nil :reason nil :remote-host nil :lifetime_minutes 60})

(defn serve-public-key
  "Return the user's public SSH key as plaintext"
  [{:keys [name]} request ldap _ _]
  (if (re-matches username-pattern name)
    (if-let [ssh-key (get-public-key name ldap)]
      (-> (ring/response ssh-key)
          (ring/header "Content-Type" "text/plain"))
      (http/not-found "User not found"))
    (http/bad-request "Invalid user name")))

(defn ensure-username [auth {:keys [username] :as req}]
  (assoc req :username (or username (:username auth))))

(defn ensure-request-keys
  "Ensure that all access request keys exist in the given map"
  [request]
  (merge empty-access-request request))

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
  [auth {:keys [hostname username remote-host reason] :as access-request} ldap ssh db]

  (log/info "Requesting access to " username "@" hostname ", remote-host=" remote-host ", reason=" reason)
  (if (ldap-auth? auth ldap)
    (let [ip (dns/to-inet-address hostname)
          auth-user (:username auth)
          networks (get-networks auth-user ldap)
          matching-networks (filter #(network-matches? % ip) networks)
          handle (sql/from-sql (first (sql/create-access-request (sq/to-sql (assoc access-request :created-by auth-user)) {:connection db})))]
      (if (empty? matching-networks)
        (let [msg (str "Forbidden. Host " ip " is not in one of the allowed networks: " (print-str networks))]
          (sql/update-access-request-status handle "DENIED" msg auth-user db)
          (http/forbidden msg))
        (let [result (execute-ssh hostname (str "grant-ssh-access --remote-host=" remote-host " " username) ssh)]
          (if (zero? (:exit result))
            (let [msg (str "Access to host " ip " for user " username " was granted.")]
              (sql/update-access-request-status handle "GRANTED" msg auth-user db)
              (http/ok msg))
            (let [msg (str "SSH command failed: " (or (:err result) (:out result)))]
              (sql/update-access-request-status handle "FAILED" msg auth-user db)
              (http/bad-request msg))))))
    (http/forbidden "Authentication failed")))

(defn validate-request
  "Validate the given access request"
  [request]
  (try (s/validate AccessRequest request)
       (catch ExceptionInfo e
         (throw (ex-info (str "Invalid request: " (.getMessage e)) {:http-code 400})))))

(defn request-access
  "Request SSH access to a specific host"
  [{:keys [request]} ring-request ldap ssh db]
  (if-let [auth (extract-auth ring-request)]
    (request-access-with-auth auth (->> request
                                        validate-request
                                        (ensure-username auth)
                                        ensure-request-keys) ldap ssh db)
    (http/unauthorized "Unauthorized. Please authenticate with username and password.")))

(defn list-access-requests
  "Return list of most recent access requests from database"
  [parameters _ _ _ db]
  (let [result (map sql/from-sql (sql/list-access-requests (sq/to-sql parameters) {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))




