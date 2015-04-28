(ns org.zalando.stups.even.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.job :refer :all]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :as ssh]))

(deftest test-revoke-access-requests
  (with-redefs [sql/get-expired-access-requests (constantly [{}])
                sql/update-access-request! (constantly 1)
                ssh/execute-ssh (constantly {:exit 0})]

    (revoke-expired-access-requests {} {})))






