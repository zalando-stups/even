(ns org.zalando.stups.even.ssh-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.even.ssh :refer :all]
    [clj-ssh.ssh :refer :all]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(defn load-key [key-id]
  (-> key-id io/resource slurp str/trim))

(def key1 (load-key "key1.pem"))

(def key2 (load-key "key2.pem"))

(def all-keys (str key1 "\n" key2))

(defn remove-files
  [paths]
  (doseq [path paths]
    (io/delete-file path true)))

(deftest write-private-keys-test
  (let [paths (write-private-keys all-keys)]
    (try
      (is (= [key1 key2]
             (map
               #(slurp %)
               paths)))
      (finally
        (remove-files paths)))))

(deftest test-execute-ssh
  (let [collected-paths (transient [])
        collected-keys (transient [])]
    (try
      (with-redefs [ssh-agent #(identity %)
                    add-identity (fn [_ {:keys [private-key-path]}]
                                   (conj! collected-paths private-key-path)
                                   (conj! collected-keys (slurp private-key-path)))
                    session (constantly "session")
                    connected? (constantly false)
                    connect (constantly "conn")
                    disconnect (constantly nil)
                    set-timeout (constantly nil)
                    ssh #(identity %2)]
        (is (= {:cmd "my-command" :agent-forwarding nil}
               (execute-ssh "my-host" "my-command" {:config {:user         "my-user"
                                                             :private-keys all-keys}})))
        (is (= [key1 key2]
               (persistent! collected-keys))))
      (finally
        (remove-files (persistent! collected-paths))))))
