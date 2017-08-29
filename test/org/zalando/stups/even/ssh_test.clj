(ns org.zalando.stups.even.ssh-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.even.ssh :refer :all]
    [clj-ssh.ssh :refer :all]
    [clojure.java.io :as io]
    [com.stuartsierra.component :as component]
    [org.zalando.stups.even.ssh :as ssh])
  (:import (org.testcontainers.images.builder ImageFromDockerfile)
           (org.testcontainers.containers GenericContainer BindMode)
           (org.testcontainers.containers.wait HostPortWaitStrategy)
           (java.time Duration)
           (java.time.temporal ChronoUnit)))

(defn test-with-pubkey
  [pub-key-file]
  (let [image (-> (ImageFromDockerfile.)
                (.withFileFromClasspath "Dockerfile", "dockerfile.sshd"))
        container (doto (GenericContainer. image)
                    (.addExposedPort (int 22))
                    (.addFileSystemBind pub-key-file "/root/.ssh/authorized_keys" BindMode/READ_ONLY)
                    (.setWaitStrategy (-> (HostPortWaitStrategy.)
                                          (.withStartupTimeout (Duration/of 60 ChronoUnit/SECONDS))))
                    (.start))
        ssh (component/start (ssh/new-ssh {:user                 "root"
                                           :port                 (.getMappedPort container 22)
                                           :private-keys         (-> "private-keys" io/resource slurp)
                                           :private-key-password "Password"
                                           :agent-forwarding     true
                                           :timeout              30}))]
    (try
      (is (= {:exit 0, :out "foobar", :err ""}
             (execute-ssh (.getContainerIpAddress container)
                          "echo -n foobar"
                          ssh)))
      (finally
        (.close container)))))

(deftest test-execute-ssh
  (doseq [key ["key1.pem.pub" "key2.pem.pub"]]
    (test-with-pubkey (format "dev-resources/%s" key))))

