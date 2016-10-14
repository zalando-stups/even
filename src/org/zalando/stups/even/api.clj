(ns org.zalando.stups.even.api
  (:require
    [org.zalando.stups.friboo.system.http :refer [def-http-component]]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [bugsbio.squirrel :as sq]
    [org.zalando.stups.even.pubkey-provider.usersvc :refer [get-public-key]]
    [org.zalando.stups.even.ssh :refer [execute-ssh]]
    [org.zalando.stups.even.sql :as sql]
    [org.zalando.stups.even.audit :as audit]
    [clojure.data.codec.base64 :as b64]
    [ring.util.http-response :as http]
    [ring.util.response :as ring]
    [org.zalando.stups.friboo.ring :as fring]
    [org.zalando.stups.friboo.user :as u]
    [org.zalando.stups.friboo.config :refer [require-config]]
    [clj-dns.core :as dns])
  (:import [clojure.lang ExceptionInfo]
           [java.util.regex Pattern]))


; most validations are now already done by Swagger1st!
(defn valid-lifetime [i] (and (pos? i) (<= i 525600)))

; most validations are now already done by Swagger1st!
(s/defschema AccessRequest
  {(s/optional-key :username)         s/Str
   :hostname                          s/Str
   :reason                            s/Str
   (s/optional-key :remote_host)      s/Str
   (s/optional-key :lifetime_minutes) (s/both s/Int (s/pred valid-lifetime))
   })

(def-http-component API "api/even-api.yaml" [ssh db usersvc http-audit-logger])

(def default-http-configuration {:http-port 8080})

(def empty-access-request {:username nil :hostname nil :reason nil :remote_host nil :lifetime_minutes 60})

(defn serve-public-key
  "Return the user's public SSH key as plaintext"
  [{:keys [name]} request _ _ usersvc]
  (if-let [ssh-key (get-public-key name usersvc)]
    (-> (ring/response ssh-key)
        (ring/header "Content-Type" "text/plain"))
    (http/not-found "User not found")))

(defn ensure-username [auth {:keys [username] :as req}]
  (assoc req :username (or username (:username auth))))

(defn ensure-request-keys
  "Ensure that all access request keys exist in the given map"
  [request]
  (merge empty-access-request request))

(defn extract-auth
  "Extract UID and team membership from ring request"
  [req]
  (if-let [uid (get-in req [:tokeninfo "uid"])]
    {:username uid
     :teams (u/require-teams req)}))

(defn resolve-hostname [hostname]
  (try
    (dns/to-inet-address hostname)
    (catch Exception e
      (throw (ex-info (str "Could not resolve hostname " hostname ": " (.getMessage e)) {:http-code 400})))))

(def team-placeholder (Pattern/quote "{team}"))

(defn get-allowed-hostnames [{:keys [username teams]} ring-request]
  (let [hostname-template (require-config (:configuration ring-request) :allowed-hostname-template)]
    (map #(.replaceAll hostname-template team-placeholder %) teams)))

(defn request-access-with-auth
  "Request server access with provided auth credentials"
  [auth {:keys [hostname username remote_host reason] :as access-request} ring-request ssh db usersvc log-fn]
  (log/info "Requesting access to " username "@" hostname ", remote-host=" remote_host ", reason=" reason)
  (let [ip (resolve-hostname hostname)
        auth-user (:username auth)
        allowed-hostnames (get-allowed-hostnames auth ring-request)
        matching-hostnames (filter #(.matches hostname %) allowed-hostnames)
        handle (sql/from-sql (first (sql/cmd-create-access-request (sq/to-sql (assoc access-request :created-by auth-user)) {:connection db})))]
    (if (empty? matching-hostnames)
      (let [msg (str "Forbidden. Host " ip " is not matching any allowed hostname: " (print-str allowed-hostnames))]
        (sql/update-access-request-status handle "DENIED" msg auth-user db)
        (http/forbidden msg))
      (let [result (execute-ssh hostname (str "grant-ssh-access --remote-host=" remote_host " " username) ssh)]
        (if (zero? (:exit result))
          (let [msg (str "Access to host " ip " for user " username " was granted.")]
            (sql/update-access-request-status handle "GRANTED" msg auth-user db)
            (log-fn (audit/create-event auth access-request ip allowed-hostnames))
            (http/ok msg))
          (let [msg (str "SSH command failed: " (or (:err result) (:out result)))]
            (sql/update-access-request-status handle "FAILED" msg auth-user db)
            (http/bad-request msg)))))))

(defn validate-request
  "Validate the given access request"
  [request]
  (try (s/validate AccessRequest request)
       (catch ExceptionInfo e
         (throw (ex-info (str "Invalid request: " (.getMessage e)) {:http-code 400})))))

(defn request-access
  "Request SSH access to a specific host"
  [{:keys [request]} ring-request ssh db usersvc {:keys [log-fn]}]
  (if-let [auth (extract-auth ring-request)]
    (request-access-with-auth auth (->> request
                                        validate-request
                                        (ensure-username auth)
                                        ensure-request-keys) ring-request ssh db usersvc log-fn)
    (http/unauthorized "Unauthorized. Please authenticate with a valid OAuth2 token.")))

(defn list-access-requests
  "Return list of most recent access requests from database"
  [parameters _ _ db _]
  (let [result (map sql/from-sql (sql/cmd-list-access-requests (sq/to-sql parameters) {:connection db}))]
    (-> (ring/response result)
        (fring/content-type-json))))
