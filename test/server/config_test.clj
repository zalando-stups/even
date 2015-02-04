(ns server.config-test
    (:require
      [clojure.test :refer :all]
      [server.config :refer :all]))

(deftest test-config-parse
         (is (= {:connect-timeout "123"} (:ldap (parse {:ldap-connect-timeout "123"} [:ldap])))))

(deftest test-load
         (is (= {:ssh-user "granting-service"} (load-defaults {}))))

(deftest test-mask
         (is (= {:a "b" :password "MASKED" :private-stuff "MASKED"} (mask {:a "b", :password "secret" :private-stuff "foobar"}))))

(deftest test-decrypt
         (is (= {:a "a" :b "b"} (decrypt {:a "a" :b "b"}))))