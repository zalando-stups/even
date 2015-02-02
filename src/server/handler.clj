(ns server.handler
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.adapter.jetty :as jetty]
            [clj-ldap.client :as ldap]
            [environ.core :refer [env]]
            ))

(def user-name-pattern #"^[a-z][a-z0-9-]{0,31}$")

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

(defroutes app-routes
           (GET "/" [] "SSH Access Granting Service")
           (GET "/health" [] "OK")
           (GET "/public-keys/:name/sshkey.pub" [name] (serve-public-key name))
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defn -main [& args]
  (jetty/run-jetty app {:port (or (:http-port env) 8080) :join? false})
  )
