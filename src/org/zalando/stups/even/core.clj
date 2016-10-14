(ns org.zalando.stups.even.core
  (:gen-class)
  (:require [com.stuartsierra.component :refer [using]]
            [environ.core :refer [env]]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system :refer [http-system-map default-http-namespaces-and]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.api :as api]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.system.audit-logger.http :as http-logger]
            [org.zalando.stups.even.job :as job]
            [org.zalando.stups.even.pubkey-provider.usersvc :refer [new-usersvc]]
            [org.zalando.stups.even.ssh :refer [new-ssh default-ssh-configuration]]
            ))

(defn new-system
  "Returns a new instance of the whole application"
  [{:keys [ssh db jobs oauth2 usersvc] :as config}]
  (http-system-map config
                   api/map->API [:ssh :db :usersvc :http-audit-logger]
                   :db (sql/map->DB {:configuration db})
                   :http-audit-logger (using
                                        (http-logger/map->HTTP {:configuration (:httplogger config)})
                                        [:tokens])
                   :tokens (oauth2/map->OAuth2TokenRefresher {:configuration oauth2
                                                              :tokens        {"user-service" ["uid"] :http-audit-logger ["uid"]}})
                   :usersvc (using (new-usersvc usersvc) [:tokens])
                   :ssh (new-ssh ssh)
                   :jobs (using (job/map->Jobs {:configuration jobs}) [:ssh :db])))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (let [configuration (config/load-configuration
                        (default-http-namespaces-and :ssh :db :jobs :oauth2 :usersvc :httplogger)
                        [api/default-http-configuration
                         default-ssh-configuration
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

