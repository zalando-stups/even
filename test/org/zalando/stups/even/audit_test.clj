(ns org.zalando.stups.even.audit-test
  (import java.net.InetAddress)
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.even.audit :as audit]))

(deftest ^:unit test-create-event
  (fact "create-event creates valid audittrail event"
    (audit/create-event
      {:username "authUserName"}
      {:hostname "hostname" :reason "schoko-reason" :remote_host "remote_host" :lifetime_minutes 66}
      (InetAddress/getByName "www.name.de")
      '("allowed1" "allowed2"))
      =>
      {:event_type
        {:name "request-ssh-access",
         :namespace "cloud.zalando.com", :version "1.0"},
         :payload
           {:access_request_lifetime 66,
            :allowed_hostnames "allowed1,allowed2",
            :host_ip "213.160.69.3",
            :hostname "hostname",
            :reason "schoko-reason",
            :remote_host "remote_host"},
         :triggered_at "formatted_datetime",
         :triggered_by
           {:id "authUserName",
            :type "EMPLOYEE_USERNAME"}}
      (provided
        (audit/get-date) => "formatted_datetime")))

