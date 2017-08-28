(ns org.zalando.stups.even.ssh-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.even.ssh :refer :all]
    [clj-ssh.ssh :refer :all]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (org.testcontainers.images.builder ImageFromDockerfile)
           (org.testcontainers.containers GenericContainer BindMode)
           (org.testcontainers.containers.wait HostPortWaitStrategy)
           (java.time Duration)
           (java.time.temporal ChronoUnit)))

(defn load-key [key-id]
  (-> key-id io/resource slurp str/trim))

(def key1 (load-key "key1.pem"))

(def key2 (load-key "key2.pem"))

(def all-keys (str key1 "\n" key2))

(defn test-with-pubkey
  [pub-key-file]
  (let [image (-> (ImageFromDockerfile.)
                (.withFileFromClasspath "Dockerfile", "dockerfile.sshd"))
        container (doto (GenericContainer. image)
                    (.addExposedPort (int 22))
                    (.addFileSystemBind pub-key-file "/root/.ssh/authorized_keys" BindMode/READ_ONLY)
                    (.setWaitStrategy (-> (HostPortWaitStrategy.)
                                          (.withStartupTimeout (Duration/of 60 ChronoUnit/SECONDS))))
                    (.start))]
    (try
      (is (= {:exit 0, :out "foobar", :err ""}
             (execute-ssh (.getContainerIpAddress container)
                          "echo -n foobar"
                          {:config {:user                 "root"
                                    :port                 (.getMappedPort container 22)
                                    :private-keys         all-keys
                                    :private-key-password "Password"
                                    :agent-forwarding     true
                                    :timeout              30}})))
      (finally
        (.close container)))))

(deftest test-execute-ssh
  (doseq [key ["key1.pem.pub" "key2.pem.pub"]]
    (test-with-pubkey (format "dev-resources/%s" key))))

