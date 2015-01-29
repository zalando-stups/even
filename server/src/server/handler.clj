(ns server.handler
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.adapter.jetty :as jetty]
            ))

(defn get-public-key [name]
  (slurp (str "public-keys/" name ".pub"))
  )

(defn serve-public-key [name]
  (get-public-key name)
  )

(defroutes app-routes
  (GET "/" [] "SSH Access Granting Service")
  (GET "/health" [] "OK")
  (GET "/public-keys/:name/sshkey.pub" [name] (serve-public-key name))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))

(defn -main [& args]
  (jetty/run-jetty app {:port 8080 :join? false})
  )
