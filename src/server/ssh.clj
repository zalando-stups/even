(ns server.ssh
    (:require [clojure.tools.logging :as log]
      [clj-ssh.ssh :refer :all]
      [server.config :as config])
    (:import
      [java.nio.file.attribute PosixFilePermissions]
      [java.nio.file.attribute FileAttribute]
      [java.nio.file Files]))

(defrecord Ssh [config])

(def owner-only
  "Java file attributes for 'owner-only' permissions"
  (into-array FileAttribute [(PosixFilePermissions/asFileAttribute (PosixFilePermissions/fromString "rwx------"))]))

(defn write-key-to-file [key]
      "Write private SSH key to a temp file, only readable by our user"
      (let [path (.toString (.resolve (Files/createTempDirectory "ssh-private-key" owner-only) "sshkey.pem"))]
           (log/info "Writing SSH private key to" path)
           (spit path key)
           path))

(defn get-private-key-path [key]
      "Return path to the private key written to disk if key is the actual key (PEM encoded)"
      (if (.startsWith key "-----BEGIN") (write-key-to-file key) key))

(defn execute-ssh [host-name command {{:keys [user private-key]} :config}]
      "Execute the given command on the remote host using the configured SSH user and private key"
      (log/info "ssh " user "@" host-name " " command)
      (let [agent (ssh-agent {:use-system-ssh-agent false
                              :known-hosts-path "/dev/null"})]
           (add-identity agent {:private-key-path (get-private-key-path private-key)})
           (let [session (session agent host-name {:username user
                                                   :strict-host-key-checking :no})]
                (with-connection session
                                 (let [result (ssh session {:cmd command})]
                                      (log/info "Result: " result)
                                      result)))))


(defn ^Ssh new-ssh [config]
      (log/info "Configuring SSH with" (config/mask config))
      (map->Ssh {:config config}))