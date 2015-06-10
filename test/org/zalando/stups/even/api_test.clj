(ns org.zalando.stups.even.api-test

  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.api :refer :all]
            [schema.core :as s]
            [org.zalando.stups.even.ssh :as ssh]
            [org.zalando.stups.even.sql :as sql]
            ))

(deftest test-access-request-validation-fails
  (are [req] (thrown? Exception (validate-request req))
             {}
             {:username "a"}
             {:hostname "a"}
             ))

(deftest test-access-request-validation-succeeds
  (are [req] (= req (validate-request req))
             ; username is optional
             {:hostname "b" :reason "a"}
             {:username "my-user" :hostname "some.host" :reason "test"}
             {:username "my-user" :hostname "1.2.3.4" :reason "test"}
             ))

(deftest test-ensure-username
  (is (= {:username "a" :blub "b"} (ensure-username {:username "a"} {:blub "b"}))))

(deftest test-request-access-wrong-network
  (with-redefs [
                get-allowed-hostnames (constantly ["odd-.*.myteam.example.org"])
                sql/create-access-request (constantly [])
                sql/update-access-request! (constantly nil)]
    (is (= {:status 403 :headers {} :body "Forbidden. Host /2.3.4.5 is not matching any allowed hostname: [odd-.*.myteam.example.org]"}
           (request-access-with-auth {:username "user1" :teams ["myteam"]} {:hostname "2.3.4.5"} {:configuration {:allowed-hostname-template "odd-.*.{team}.example.org"}} {} {} {})))))

(deftest test-request-access-success
  (with-redefs [
                sql/create-access-request (constantly [])
                sql/update-access-request! (constantly nil)
                resolve-hostname (constantly "odd-eu-west-1.myteam.example.org/127.0.0.1")
                ssh/execute-ssh (constantly {:exit 0})]
    (is (= {:status 200 :headers {} :body "Access to host odd-eu-west-1.myteam.example.org/127.0.0.1 for user user1 was granted."}
           (request-access-with-auth {:username "user1" :teams ["myteam"]} {:hostname "odd-eu-west-1.myteam.example.org" :username "user1"} {:configuration {:allowed-hostname-template "odd-.*.{team}.example.org"}} {} {} {})))))

(deftest test-request-no-auth
  (is (= {:status 401 :headers {} :body "Unauthorized. Please authenticate with a valid OAuth2 token."} (request-access {:request {}} {} {} {} {}))))
