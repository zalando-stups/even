(ns server.api.router-test
    (:require [clojure.test :refer :all]
              [server.api.router :refer :all]
              [schema.core :as s]))

(deftest test-access-request-validation-fails
         (are [req] (thrown? Exception (s/validate AccessRequest req))
              {}
              {:user-name "a"}
              {:host-name "a"}
              ))

(deftest test-access-request-validation-succeeds
         (are [req] (= req (s/validate AccessRequest req))
              {:user-name "a" :host-name "b" :reason "a"}
              {:user-name "my-user" :host-name "some.host" :reason "test"}
              {:user-name "my-user" :host-name "1.2.3.4" :reason "test"}
              ))

(deftest test-parse-authorization
         (is (= {:username "a" :password "b"} (parse-authorization "Basic YTpi"))))