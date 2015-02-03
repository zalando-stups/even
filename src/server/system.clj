(ns server.system
    (:gen-class)
    (:require [com.stuartsierra.component :as component :refer [using]]
      [environ.core :refer [env]]
      [server.api.http-server :refer [new-http-server]]
      [server.api.router :refer [new-router]]
      [server.pubkey-provider.ldap :refer [new-ldap]]
      [server.config :as config]
      [server.ssh :refer [new-ssh]]
      ))


(defn new-system [config]
      "Returns a new instance of the whole application"
      (let [{:keys [ldap http ssh]} (config/parse (config/load-defaults config) [:ldap :http :ssh])]
      (component/system-map
        :http-server (using (new-http-server http) [:router])
        :router (using (new-router) [:ldap :ssh])
        :ldap (using (new-ldap ldap) [])
        :ssh (using (new-ssh ssh) [])
        )))

(defn start [system]
      (component/start system))

(defn stop [system]
      (component/stop system))

(defn -main [& args]
      (let [system (new-system env)]

           (.addShutdownHook
             (Runtime/getRuntime)
             (Thread. (fn [] (stop system))))

           (start system)))
