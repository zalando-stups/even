(ns org.zalando.stups.even.api
  (:require
    [org.zalando.stups.friboo.system.http :refer [def-http-component]]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [org.zalando.stups.even.pubkey-provider.ldap :refer [get-public-key ldap-auth? get-networks]]
    [org.zalando.stups.even.ssh :refer [execute-ssh]]
    [clojure.data.codec.base64 :as b64]
    [ring.util.http-response :as http]
    [clj-dns.core :as dns]
    [org.zalando.stups.even.net :refer [network-matches?]])
  (:import [org.apache.commons.net.util SubnetUtils]))

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
             {(s/optional-key :username) (s/both String (s/pred matches-username-pattern))
              :hostname (s/both String (s/pred matches-hostname-pattern))
              :reason (s/both String (s/pred non-empty))
              (s/optional-key :remote-host) (s/both String (s/pred matches-hostname-pattern))
              })

(def-http-component API "api/even-api.yaml" [ldap ssh])

(def default-http-configuration {:http-port 8080})


(defmethod compojure.api.meta/restructure-param :auth
  [_ authorization {:keys [body] :as acc}]
  "Parse Authorization"
  (-> acc
      (update-in [:lets] into [{{authorization "authorization"} :headers} '+compojure-api-request+])
      (assoc :body `((if (string? ~authorization)
                       (do ~@body)
                       (http/unauthorized "Auth required"))))))

(defn serve-public-key [name ldap]
  (if (re-matches username-pattern name)
    (or (get-public-key name ldap)
        (http/not-found "User not found"))
    (http/bad-request "Invalid user name")))

(defn ensure-username [auth {:keys [username] :as req}]
  (assoc req :username (or username (:username auth))))

(defn request-access [auth {:keys [hostname username reason remote-host] :as req} ssh ldap]
  (log/info "Requesting access for " req)
  (if (ldap-auth? auth ldap)
    (let [ip (dns/to-inet-address hostname)
          networks (get-networks (:username auth) ldap)
          matching-networks (filter #(network-matches? % ip) networks)]
      (if (empty? matching-networks)
        (http/forbidden (str "Forbidden. Host " ip " is not in one of the allowed networks: " (print-str networks)))
        (let [result (execute-ssh hostname (str "grant-ssh-access --remote-host=" remote-host " " username) ssh)]
          (if (zero? (:exit result))
            (http/ok (str "Access to host " ip " for user " username " was granted."))
            (http/bad-request (str "Failed: " result))))))
    (http/forbidden "Authentication failed")))

(defn parse-authorization [authorization]
  "Parse HTTP Basic Authorization header"
  (-> authorization
      (clojure.string/replace-first "Basic " "")
      .getBytes
      b64/decode
      String.
      (clojure.string/split #":" 2)
      (#(zipmap [:username :password] %))))



(defn- exception-logging [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Caught exception in web-tier")
        (throw e)))))

(defn new-app [router]
  (api-middleware
    (routes/with-routes
      (swagger-ui)
      (swagger-docs :title "SSH Access Granting Service")
      (exception-logging (api-routes router)))))


(defn ^Router new-router []
  (log/info "Configuring router")
  (map->Router {}))