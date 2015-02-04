(ns server.ssh-test
    (:require
      [clojure.test :refer :all]
      [server.ssh :refer :all]))

(deftest test-get-private-key-path
         (is (.startsWith (get-private-key-path "-----BEGIN RSA PRIVATE KEY-----TEST") "/")))
