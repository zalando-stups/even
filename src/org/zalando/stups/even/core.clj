(ns org.zalando.stups.even.core
    (:gen-class)
    (:require [com.stuartsierra.component :as component :refer [using]]
      [environ.core :refer [env]]
      [org.zalando.stups.even.api :as api]
      [org.zalando.stups.even.pubkey-provider.ldap :refer [new-ldap]]
      [org.zalando.stups.even.config :as config]
      [org.zalando.stups.even.ssh :refer [new-ssh]]
      ))


(defn new-system [config]
      "Returns a new instance of the whole application"
      (let [{:keys [ldap http ssh]} (config/parse (config/decrypt (config/load-defaults config)) [:ldap :http :ssh])]
      (component/system-map
        :api (using (api/map->API {:configuration config}) [:ldap :ssh]))
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
