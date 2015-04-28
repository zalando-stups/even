(ns org.zalando.stups.even.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :refer [execute-ssh]]
            [overtone.at-at :refer [every]]
            ))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         30000
   :jobs-initial-delay-ms 1000})


(defn revoke-expired-access-request
  "Revoke a single expired access request"
  [ssh db {:keys [hostname remote_host username] :as req}]
  (log/info "Revoking expired access request %s.." req)
  (let [result (execute-ssh hostname (str "revoke-ssh-access --remote-host=" remote_host " " username) ssh)]
    (if (zero? (:exit result))
      (let [msg (str "Access to host " hostname " for user " username " was revoked.")]
        (sql/update-access-request-status req "REVOKED" msg "job" db)
        (log/info msg))
      (let [msg (str "SSH command failed: " (or (:err result) (:out result)))]
        (sql/update-access-request-status req "GRANTED" msg "job" db)
        (log/error {} msg)))))

(defn revoke-expired-access-requests
  "Revoke all expired access requests"
  [ssh db]
  (let [expired-requests (sql/get-expired-access-requests {} {:connection db})]
    (log/info "Revoking %s expired access requests.." (count expired-requests))
    (doseq [req expired-requests]
      (revoke-expired-access-request ssh db (sql/from-sql req)))))

(defn run-revoke-expired-access-requests
  "CRON job to cleanup locks and expire access requests"
  [ssh db configuration]
  (try
    (sql/clean-up-old-locks! {} {:connection db})
    (let [lock (sql/from-sql (first (sql/acquire-lock {:resource_name "revoke-expired-access-requests" :created_by "job"} {:connection db})))]
      (revoke-expired-access-requests ssh db)
      (sql/release-lock! lock {:connection db}))
    (catch Exception e
      (log/error e "Caught exception while executing CRON job: %s" (str e)))))

(def-cron-component
  Jobs [ssh db]

  (let [{:keys [every-ms initial-delay-ms]} configuration]
    (every every-ms #(run-revoke-expired-access-requests ssh db configuration) pool
           :initial-delay initial-delay-ms
           :desc "revoke expired access requests")))
