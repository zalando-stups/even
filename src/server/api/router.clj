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
      [clj-ssh.ssh :refer :all]
      ))

(def user-name-pattern #"^[a-z][a-z0-9-]{0,31}$")

(s/defschema AccessRequest
             {:user-name String
              :host-name String
              })

(defrecord Router [config])

(defn ldap-connect [] (ldap/connect {:host (:ldap-host env)
                                     :bind-dn (:ldap-bind-dn env)
                                     :password (:ldap-password env)
                                     :ssl? (Boolean/parseBoolean (:ldap-ssl env))
                                     }))

(defn get-ldap-user-dn [name]
      "Build LDAP DN for a given user name"
      (str "uid=" name "," (:ldap-base-dn env)))

(defn get-public-key [name]
      "Get a user's public SSH key"
      (let [conn (ldap-connect)]
           (:sshPublicKey (ldap/get conn (get-ldap-user-dn name) [:sshPublicKey]))))


(defn serve-public-key [name]
      (if (re-matches user-name-pattern name)
        (or (get-public-key name)
            {:status 404
             :body "User not found"})
        {:status 400
         :body "Invalid user name"}))

(defn execute-ssh [host-name command config]
      (let [user-name (:ssh-user config)]
           (log/info "ssh " user-name "@" host-name " " command)
           (let [agent (ssh-agent {:use-system-ssh-agent false})]
                (add-identity agent {:private-key-path (:ssh-private-key config)})
                (let [session (session agent host-name {:username user-name :strict-host-key-checking :no})]
                     (with-connection session
                                      (let [result (ssh session {:cmd command})]
                                           (log/info "Result: " result)
                                           result))))))

(defn request-access [config req]
      (log/info "Requesting access for " req)
      (let [result (execute-ssh (:host-name req) (str "grant-ssh-access " (:user-name req)) config)]
           (if (= (:exit result) 0)
             {:status 200
              :body "Access requested"}
             {:status 400
              :body (str "Failed: " result)})
           ))

(defn- api-routes [{:keys [config]}]
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
                  (request-access config request)))
         (swaggered
           "Public Keys"
           :description "Expose SSH public keys"
           (GET* "/public-keys/:name/sshkey.pub" [name]
                 :summary "Download the user's SSH public key"
                 (serve-public-key name)))))


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


(defn ^Router new-router [config]
      (log/info "Configuring router with" config)
      (map->Router {:config config}))