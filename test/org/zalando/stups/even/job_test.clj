(ns org.zalando.stups.even.job-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.job :refer :all]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :as ssh]))


(deftest test-get-revoke-ssh-options
  (is (= ["revoke-ssh-access" "myuser" "--remote-host" "myremote" "--keep-local"] (get-revoke-ssh-access-options "myremote" "myuser" 1))))

(deftest test-revoke-access-requests
  (with-redefs [sql/get-expired-access-requests (constantly [{}])
                sql/update-access-request! (constantly 1)
                sql/count-remaining-granted-access-requests (constantly [{:count 0}])
                ssh/execute-ssh (constantly {:exit 0})]

    (revoke-expired-access-requests {} {})))






