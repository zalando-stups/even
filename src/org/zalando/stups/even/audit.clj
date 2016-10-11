(ns org.zalando.stups.even.audit
  (:require [clj-time.format :as tf]
            [clj-time.core :as t]))

(def date-formatter
  (tf/formatters :date-time))

(defn get-date
  []
  (tf/unparse date-formatter (t/now)))

(defn create-event
  [auth access-request ip hostnames]
  {:event_type   {:namespace "cloud.zalando.com"
                  :name      "request-ssh-access"
                  :version   "1.0"}
   :triggered_at (get-date)
   :triggered_by {:type       "USER"
                  :id         (:username auth)
                  :additional {:realm (:realm auth)}}
   :payload      {:hostname (:hostname access-request)
                  :host_ip (.getHostAddress ip)
                  :reason (:reason access-request)
                  :remote_host (:remote_host access-request)
                  :ssh_key_lifetime (:lifetime_minutes access-request)
                  :allowed_hostnames hostnames}})
