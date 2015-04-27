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


(defn revoke-expired-access-request [ssh db {:keys [hostname remote_host username] :as req}]
  (log/info "Revoking expired access request" req)
  (let [result (execute-ssh hostname (str "revoke-ssh-access --remote-host=" remote_host " " username) ssh)]
    (if (zero? (:exit result))
      (let [msg (str "Access to host " hostname " for user " username " was revoked.")]
        (sql/update-access-request-status req "REVOKED" msg "job" db)
        (log/info msg))
      (let [msg (str "SSH command failed: " (or (:err result) (:out result)))]
        (sql/update-access-request-status req "FAILED" msg "job" db)
        (log/error {} msg)))))

(defn revoke-expired-access-requests [ssh db configuration]
  (let [expired-requests (sql/get-expired-access-requests {} {:connection db})]
    (log/info "Revoking" (count expired-requests) "requests..")
    (doseq [req expired-requests]
      (revoke-expired-access-request ssh db req))))

(defn run-revoke-expired-access-requests [ssh db configuration]
  (try
     (revoke-expired-access-requests ssh db configuration)
     (catch Exception e
       (log/error e "Caught exception while executing CRON job: %s" (str e)))))

(def-cron-component
  Jobs [ssh db]

  (let [{:keys [every-ms initial-delay-ms]} configuration]
    (every every-ms #(run-revoke-expired-access-requests ssh db configuration) pool
           :initial-delay initial-delay-ms
           :desc "revoke expired access requests")))