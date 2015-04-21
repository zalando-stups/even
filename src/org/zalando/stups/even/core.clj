(ns org.zalando.stups.even.core
    (:gen-class)
    (:require [com.stuartsierra.component :as component :refer [using]]
      [environ.core :refer [env]]
              [org.zalando.stups.friboo.config :as config]
              [org.zalando.stups.friboo.system :as system]
              [org.zalando.stups.friboo.log :as log]
      [org.zalando.stups.even.api :as api]
      [org.zalando.stups.even.pubkey-provider.ldap :refer [new-ldap]]
      [org.zalando.stups.even.ssh :refer [new-ssh]]
      ))


(defn new-system [config]
      "Returns a new instance of the whole application"
      (let [{:keys [ldap http ssh]} config]
      (component/system-map
        :api (using (api/map->API {:configuration config}) [:ldap :ssh])
        :ldap (using (new-ldap ldap) [])
        :ssh (using (new-ssh ssh) [])
        )))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (let [configuration (config/load-configuration
                        [:http :ldap :ssh]
                        [api/default-http-configuration
                         {}
                         {}])

        system (new-system configuration)]
    (system/run configuration system)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start system because of %s." (str e))
      (System/exit 1))))

