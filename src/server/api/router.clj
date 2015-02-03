(ns server.api.router
    (:require
      [compojure.api.sweet :refer :all]
      [compojure.api.middleware :refer :all]
      [compojure.api.routes :as routes]
      [ring.adapter.jetty :as jetty]
      [clj-ldap.client :as ldap]
      [environ.core :refer [env]]
      [clojure.tools.logging :as log]
      [schema.core :as s]
      [server.pubkey-provider.ldap :refer [get-public-key]]
      [server.ssh :refer [execute-ssh]]
      ))

(def user-name-pattern #"^[a-z][a-z0-9-]{0,31}$")

(s/defschema AccessRequest
             {:user-name String
              :host-name String
              })

(defrecord Router [ldap ssh])

(defn serve-public-key [name ldap]
      (if (re-matches user-name-pattern name)
        (or (get-public-key name ldap)
            {:status 404
             :body "User not found"})
        {:status 400
         :body "Invalid user name"}))

(defn request-access [{:keys [host-name user-name] :as req} ssh]
      (log/info "Requesting access for " req)
      (let [result (execute-ssh host-name (str "grant-ssh-access " user-name) ssh)]
           (if (= (:exit result) 0)
             {:status 200
              :body "Access requested"}
             {:status 400
              :body (str "Failed: " result)})
           ))

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
             {:status 200}))

         (swaggered
           "Access Requests"
           :description "Manage access requests"
           (POST* "/access-requests" []
                  :summary "Request SSH access to a single host"
                  :return String
                  :body [request AccessRequest]
                  ;:header-params [authorization :- String]
                  (request-access request ssh)))
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