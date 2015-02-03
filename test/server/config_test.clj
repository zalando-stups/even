(ns server.config-test
    (:require
      [clojure.test :refer :all]
      [server.config :refer :all]))

(deftest test-config-parse
         (is (= {:connect-timeout "123"} (:ldap (parse {:ldap-connect-timeout "123"} [:ldap])))))