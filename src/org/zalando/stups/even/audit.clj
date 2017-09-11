(ns org.zalando.stups.even.audit
  (:require [clj-time.format :as tf]
            [clojure.string :as str]
            [clj-time.core :as t]))

(def date-formatter
  (tf/formatters :date-time))

(defn get-date
  []
  (tf/unparse date-formatter (t/now)))

(defn drop-nil-values
  [record]
  (into {} (remove (comp nil? second) record)))

(defn create-event
  [auth access-request ip allowed-hostnames]
  {:event_type   {:namespace "cloud.zalando.com"
                  :name      "request-ssh-access"
                  :version   "1.1"}
   :triggered_at (get-date)
   :triggered_by {:type       "EMPLOYEE_USERNAME"
                  :id         (:username auth)}
   :payload      (drop-nil-values
                   {:hostname (:hostname access-request)
                    :host_ip (.getHostAddress ip)
                    :reason (:reason access-request)
                    :remote_host (:remote_host access-request)
                    :access_request_lifetime (:lifetime_minutes access-request)
                    :allowed_hostnames (str/join "," allowed-hostnames)})})
