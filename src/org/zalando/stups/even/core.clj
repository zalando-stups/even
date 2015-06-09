(ns org.zalando.stups.even.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component :refer [using]]
            [environ.core :refer [env]]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.api :as api]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.even.job :as job]
            [org.zalando.stups.even.pubkey-provider.ldap :refer [new-ldap default-ldap-configuration]]
            [org.zalando.stups.even.ssh :refer [new-ssh default-ssh-configuration]]
            ))

(defn new-system
  "Returns a new instance of the whole application"
  [config]

  (let [{:keys [ldap http ssh db jobs oauth2]} config]
    (component/system-map
      :db (sql/map->DB {:configuration db})
      :tokens (oauth2/map->OAUth2TokenRefresher {:configuration oauth2
                                                 :tokens        {:user-service-ro-api ["uid"]}})
      :ldap (new-ldap ldap)
      :ssh (new-ssh ssh)
      :api (using (api/map->API {:configuration http}) [:ldap :ssh :db])
      :jobs (using (job/map->Jobs {:configuration jobs}) [:ssh :db]))))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (System/setProperty "hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds" "15000")
  (let [configuration (config/load-configuration
                        [:http :ldap :ssh :db :jobs]
                        [api/default-http-configuration
                         default-ssh-configuration
                         default-ldap-configuration
                         sql/default-db-configuration
                         job/default-configuration
                         default-configuration])

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

