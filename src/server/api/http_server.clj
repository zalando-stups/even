(ns server.api.http-server
    (:require [clojure.tools.logging :as log]
      [com.stuartsierra.component :as component]
      [ring.adapter.jetty :as jetty]
      [compojure.api.sweet :refer :all]
      [compojure.api.middleware :refer [api-middleware]]
      [server.api.router :as router]))

(defrecord HTTPServer [port router server]
           component/Lifecycle

           (start [this]
                  (if server
                    this
                    (let [server (jetty/run-jetty (router/new-app router) {:port port :join? false})]
                         (assoc this :server server))))

           (stop [this]
                 (if server
                   (.stop server)
                   (dissoc this :server))))

(defn ^HTTPServer new-http-server [{:keys [port] :or {port 8080} :as config}]
      (log/info "Configuring http server with" config)
      (map->HTTPServer {:port (if (number? port) port (Integer/parseInt port))}))
