(ns org.zalando.stups.even.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.job :refer :all]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :as ssh]))


(deftest test-get-revoke-ssh-options
  (is (= ["revoke-ssh-access" "myuser" "--remote-host" "myremote" "--keep-local"] (get-revoke-ssh-access-options "myremote" "myuser" 1))))

(deftest test-acquire-lock
  (with-redefs [sql/acquire-lock (constantly [{:l_id 123}])]
    (is (= {:id 123} (acquire-lock {})))))

(deftest test-acquire-lock-fail
  (with-redefs [sql/acquire-lock (fn [_ _] (throw (Exception. "duplicate key value violates unique constraint")))]
    (is (nil? (acquire-lock {})))))

(deftest test-revoke-access-requests
  (with-redefs [sql/get-expired-access-requests (constantly [{}])
                sql/update-access-request! (constantly 1)
                sql/count-remaining-granted-access-requests (constantly [{:count 0}])
                ssh/execute-ssh (constantly {:exit 0})]

    (revoke-expired-access-requests {} {})))

(deftest test-revoke-access-requests-ssh-failure
  (with-redefs [sql/get-expired-access-requests (constantly [{}])
                sql/update-access-request! (constantly 1)
                sql/count-remaining-granted-access-requests (constantly [{:count 0}])
                ssh/execute-ssh (constantly {:exit 1})]

    (revoke-expired-access-requests {} {})))

(deftest test-run-revoke-expired-access-requests
  (with-redefs [sql/clean-up-old-locks! (constantly 1)
                acquire-lock (constantly {:id 123})
                revoke-expired-access-requests #(throw (Exception. (str "error" %1 %2)))
                sql/release-lock! (constantly nil)]
    (run-revoke-expired-access-requests {} {})))



