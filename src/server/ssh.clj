(ns server.ssh
    (:require [clojure.tools.logging :as log]
      [clj-ssh.ssh :refer :all]))

(defrecord Ssh [config])

(def owner-only (into-array java.nio.file.attribute.FileAttribute [(java.nio.file.attribute.PosixFilePermissions/asFileAttribute (java.nio.file.attribute.PosixFilePermissions/fromString "rwx------"))]))

(defn write-key-to-file [key]
      (let [path (.toString (.resolve (java.nio.file.Files/createTempDirectory "ssh-private-key" owner-only) "sshkey.pem"))]
           (log/info "Writing SSH private key to" path)
           (spit path key)
           path))

(defn get-private-key-path [key]
      (if (.startsWith key "-----BEGIN") (write-key-to-file key) key))

(defn execute-ssh [host-name command {{:keys [user private-key]} :config}]
      (log/info "ssh " user "@" host-name " " command)
      (let [agent (ssh-agent {:use-system-ssh-agent false})]
           (add-identity agent {:private-key-path (get-private-key-path private-key)})
           (let [session (session agent host-name {:username user :strict-host-key-checking :no})]
                (with-connection session
                                 (let [result (ssh session {:cmd command})]
                                      (log/info "Result: " result)
                                      result)))))


(defn ^Ssh new-ssh [config]
      (log/info "Configuring SSH with" config)
      (map->Ssh {:config config}))