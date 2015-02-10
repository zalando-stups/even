(ns server.api.router
  (:require
    [compojure.api.sweet :refer :all]
    [compojure.api.middleware :refer :all]
    [compojure.api.routes :as routes]
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [server.pubkey-provider.ldap :refer [get-public-key ldap-auth?]]
    [server.ssh :refer [execute-ssh]]
    [clojure.data.codec.base64 :as b64]
    [ring.util.http-response :as http]
    ))

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
              })

(defrecord Router [ldap ssh])


(defmethod compojure.api.meta/restructure-param :auth
  [_ authorization {:keys [parameters lets body middlewares] :as acc}]
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

(defn request-access [auth {:keys [hostname username reason] :as req} ssh ldap]
  (log/info "Requesting access for " req)
  (if (ldap-auth? auth ldap)
      (let [result (execute-ssh hostname (str "grant-ssh-access " username) ssh)]
        (if (= (:exit result) 0)
            (http/ok "Access granted")
            (http/bad-request (str "Failed: " result))))
      (http/forbidden "Login failed")))

(defn parse-authorization [authorization]
  "Parse HTTP Basic Authorization header"
  (-> authorization
      (clojure.string/replace-first "Basic " "")
      .getBytes
      b64/decode
      String.
      (clojure.string/split #":" 2)
      (#(zipmap [:username :password] %))))

(defn- api-routes [{:keys [ldap ssh]}]
  (routes/with-routes
    (swaggered
      "System"
      :description "Basic system operations"

      (GET*
        "/health" []
        :summary "Performs a health check"
        (log/info "Checking health")
        ; TODO perform a database connection
        (http/ok "OK")))

  (swaggered
    "Access Requests"
    :description "Manage access requests"
    (POST* "/access-requests" []
           :summary "Request SSH access to a single host"
           :return String
           :body [request AccessRequest]
           ;:header-params [authorization :- String]
           :auth authorization
           (let [auth (parse-authorization authorization)]
                (request-access auth (ensure-username auth request) ssh ldap))))
  (swaggered
    "Public Keys"
    :description "Expose SSH public keys"
    (GET* "/public-keys/:name/sshkey.pub" [name]
          :summary "Download the user's SSH public key"
          (serve-public-key name ldap)))))


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