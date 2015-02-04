(ns server.ssh-test
    (:require
      [clojure.test :refer :all]
      [server.ssh :refer :all]
      [clj-ssh.ssh :refer :all]))

(deftest test-get-private-key-path
         (is (.startsWith (get-private-key-path "-----BEGIN RSA PRIVATE KEY-----TEST") "/")))

(deftest test-execute-ssh
         (with-redefs [ssh-agent #(identity %)
                       add-identity (constantly nil)
                       session (constantly "session")
                       connected? (constantly false)
                       connect (constantly "conn")
                       disconnect (constantly nil)
                       ssh #(identity %2)]
                      (is (= {:cmd "my-command"} (execute-ssh "my-host" "my-command" {:config {:user "my-user"
                                                                                     :private-key "sshkey.pem"}})))))