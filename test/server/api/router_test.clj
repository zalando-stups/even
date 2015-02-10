(ns server.api.router-test
    (:require [clojure.test :refer :all]
              [server.api.router :refer :all]
              [schema.core :as s]))

(deftest test-access-request-validation-fails
         (are [req] (thrown? Exception (s/validate AccessRequest req))
              {}
              {:username "a"}
              {:hostname "a"}
              ))

(deftest test-access-request-validation-succeeds
         (are [req] (= req (s/validate AccessRequest req))
              ; username is optional
              {:hostname "b" :reason "a"}
              {:username "my-user" :hostname "some.host" :reason "test"}
              {:username "my-user" :hostname "1.2.3.4" :reason "test"}
              ))

(deftest test-parse-authorization
         (is (= {:username "a" :password "b"} (parse-authorization "Basic YTpi"))))

(deftest test-ensure-username
  (is (= {:username "a" :blub "b"} (ensure-username {:username "a"} {:blub "b"}))))