(ns org.zalando.stups.even.ssh
  (:require [clojure.tools.logging :as log]
            [clj-ssh.ssh :refer :all]
            [clojure.java.io :as io]
            [org.zalando.stups.friboo.config :as config]
            [com.netflix.hystrix.core :refer [defcommand]])
  (:import
    [java.nio.file.attribute PosixFilePermissions]
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]))

(defrecord Ssh [config])

(def default-ssh-configuration {:ssh-user             "granting-service"
                                :ssh-port             22
                                :ssh-agent-forwarding true
                                :ssh-timeout          24000})

(def owner-only
  "Java file attributes for 'owner-only' permissions"
  (into-array FileAttribute [(PosixFilePermissions/asFileAttribute (PosixFilePermissions/fromString "rwx------"))]))

(defn write-key-to-file
  "Write private SSH key to a temp file, only readable by our user"
  [key]
  (let [path (str (.resolve (Files/createTempDirectory "ssh-private-key" owner-only) "sshkey.pem"))]
    (log/info "Writing SSH private key to" path)
    (spit path key)
    path))

(defn get-private-key-path
  "Return path to the private key written to disk if key is the actual key (PEM encoded)"
  [key]
  (if (.startsWith key "-----BEGIN") (write-key-to-file key) key))

(defn set-timeout [session timeout]
  (.setTimeout session timeout))

(defn execute-ssh
  "Execute the given command on the remote host using the configured SSH user and private key"
  [hostname command {{:keys [user private-key port agent-forwarding timeout]} :config}]
  (log/info "ssh" user "@" hostname command)
  (let [agent (ssh-agent {:use-system-ssh-agent false
                          :known-hosts-path     "/dev/null"})
        private-key-path (get-private-key-path private-key)]
    (add-identity agent {:private-key-path private-key-path})
    (let [session (session agent hostname {:username                 user
                                           :port                     port
                                           :strict-host-key-checking :no})]
      (set-timeout session timeout)
      (try
        (with-connection session
                         (let [result (ssh session {:cmd              command
                                                    :agent-forwarding agent-forwarding})]
                           (log/info "Result: " result)
                           result))
        (catch Exception e
          {:exit 255 :err (.getMessage e) :out ""})
        (finally
          (io/delete-file private-key-path true))))))


(defn ^Ssh new-ssh [config]
  (log/info "Configuring SSH with" (config/mask config))
  (map->Ssh {:config config}))
