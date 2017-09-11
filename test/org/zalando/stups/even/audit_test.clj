(ns org.zalando.stups.even.audit-test
  (:import java.net.InetAddress)
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.even.audit :as audit]))

(deftest ^:unit test-create-event
  (facts "about creating audittrail events"
    (fact "creates valid audittrail event with all possible data"
      (audit/create-event
        {:username "authUserName"}
        {:hostname "hostname" :reason "schoko-reason" :remote_host "remote_host" :lifetime_minutes 66}
        (InetAddress/getByName "www.name.de")
        '("allowed1" "allowed2"))
      =>
        {:event_type
          {:name "request-ssh-access",
           :namespace "cloud.zalando.com", :version "1.1"},
           :payload
             {:access_request_lifetime 66,
              :allowed_hostnames "allowed1,allowed2",
              :host_ip "213.160.69.3",
              :hostname "hostname",
              :reason "schoko-reason",
              :remote_host "remote_host"},
           :triggered_at .date.,
           :triggered_by
             {:id "authUserName",
              :type "EMPLOYEE_USERNAME"}}
        (provided
          (audit/get-date) => .date.))

    (fact "creates valid audittrail event with only needed data leaving out everything else"
      (audit/create-event
        {:username "authUserName"}
        {:hostname "hostname" :lifetime_minutes 66}
        (InetAddress/getByName "www.name.de")
        '("allowed1" "allowed2"))
      =>
        {:event_type
          {:name "request-ssh-access",
           :namespace "cloud.zalando.com", :version "1.1"},
           :payload
             {:access_request_lifetime 66,
              :allowed_hostnames "allowed1,allowed2",
              :host_ip "213.160.69.3",
              :hostname "hostname"},
           :triggered_at .date.,
           :triggered_by
             {:id "authUserName",
              :type "EMPLOYEE_USERNAME"}}
        (provided
          (audit/get-date) => .date.))))

