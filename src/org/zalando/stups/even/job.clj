(ns org.zalando.stups.even.job
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component job]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :refer [execute-ssh]]
            [overtone.at-at :refer [every]]
            ))

(def default-configuration
  {:jobs-cpu-count        1
   :jobs-every-ms         30000
   :jobs-initial-delay-ms 1000})

(def one-minute-millis
  (* 60 1000))

(defn get-revoke-ssh-access-options
  "Return command line options for the SSH forced command script"
  [remote_host username remaining-count]
  (-> ["revoke-ssh-access" username]
      (concat (if (nil? remote_host) [] ["--remote-host" remote_host]))
      (concat (if (pos? remaining-count) ["--keep-local"] []))))

(defn retry-revocation-without-remote-host?
  "Should we retry the revoke-ssh-access command without remote host (e.g. because it was shut down)?"
  [{:keys [created lifetime_minutes status status_reason]}]
  (let [age_minutes (/ (- (System/currentTimeMillis) (.getTime created)) one-minute-millis)
        expiration_minutes (- age_minutes lifetime_minutes)
        expired_for_more_than_one_hour (> expiration_minutes 60)
        was_timeout (and (= status "EXPIRED") (.contains status_reason "Connection timed out"))]
    (and expired_for_more_than_one_hour was_timeout)))

(defn revoke-ssh-access
  "Revoke SSH access to the given host/remote host"
  [ssh db {:keys [id hostname remote_host username created lifetime_minutes status status_reason] :as req}]
  (let [remaining-count (:count (first (sql/count-remaining-granted-access-requests {:hostname hostname :id id} {:connection db})))
        remote_host_or_nil (when-not (retry-revocation-without-remote-host? req) remote_host)
        options (get-revoke-ssh-access-options remote_host_or_nil username remaining-count)]
    (execute-ssh hostname (clojure.string/join " " options) ssh)))

(defn revoke-expired-access-request
  "Revoke a single expired access request"
  [ssh db {:keys [hostname remote_host username] :as req}]
  (log/info "Revoking expired access request %s.." req)
  (let [result (revoke-ssh-access ssh db req)]
    (if (zero? (:exit result))
      (let [msg (str "Access to host " hostname " for user " username " was revoked.")]
        (sql/update-access-request-status req "REVOKED" msg "job" db)
        (log/info msg))
      (let [msg (str "SSH revocation command failed: " (or (:err result) (:out result)))]
        (sql/update-access-request-status req "EXPIRED" msg "job" db)
        (log/warn msg)))))

(defn revoke-expired-access-requests
  "Revoke all expired access requests"
  [ssh db]
  (let [expired-requests (map sql/from-sql (sql/get-expired-access-requests {} {:connection db}))]
    (log/info "Revoking %s expired access requests.." (count expired-requests))
    (doseq [req expired-requests]
      (sql/update-access-request-status req "EXPIRED" "Request lifetime exceeded" "job" db)
      (revoke-expired-access-request ssh db req))))

(defn acquire-lock [db]
  (try
    (sql/from-sql (first (sql/acquire-lock {:resource_name "revoke-expired-access-requests" :created_by "job"} {:connection db})))
    (catch Exception e
      (if (.contains (str e) "duplicate key value violates unique constraint")
        (log/info "Could not acquire lock: resource already locked")
        (throw e)))))

(defn run-revoke-expired-access-requests
  "CRON job to cleanup locks and expire access requests"
  [ssh db]
  (try
    (sql/clean-up-old-locks! {} {:connection db})
    (if-let [lock (acquire-lock db)]
      (try
        (revoke-expired-access-requests ssh db)
        (finally (sql/release-lock! lock {:connection db}))))
    ; IMPORTANT: we need to catch all Throwables because yesql uses "assert" in some cases
    (catch Throwable e
      (log/error e "Caught exception while executing CRON job: %s" (str e)))))

(def-cron-component
  Jobs [ssh db]

  (let [{:keys [every-ms initial-delay-ms]} configuration]
    (every every-ms (job run-revoke-expired-access-requests ssh db) pool
           :initial-delay initial-delay-ms
           :desc "revoke expired access requests")))
