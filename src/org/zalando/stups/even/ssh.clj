(ns org.zalando.stups.even.ssh
  (:require [clojure.tools.logging :as log]
            [clj-ssh.ssh :refer :all]
            [clojure.java.io :as io]
            [org.zalando.stups.friboo.config :as config]
            [com.netflix.hystrix.core :refer [defcommand]]
            [clojure.string :as str])
  (:import
    [java.nio.file.attribute PosixFilePermissions]
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]
    (java.util UUID)
    (java.nio.charset StandardCharsets)
    (com.jcraft.jsch JSch)))

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
  (let [key-filename (format "%s.pem" (UUID/randomUUID))
        path (str (Files/createTempFile "ssh-private-key" key-filename owner-only))]
    (log/info "Writing SSH private key to" path)
    (spit path key)
    path))

(defn split-keys
  "Extract all keys contained in a single string into a sequence of keys"
  [key-str]
  (->> (str/split key-str #"(?=-----BEGIN)")
    (map str/trim)
    (filter not-empty)))

(defn write-private-keys
  "Takes a string containing multiple private keys, writes each of them to an individual file and returns
   a sequence of paths to these files"
  [key-str]
  (map write-key-to-file (split-keys key-str)))

(defn set-timeout [session timeout]
  (.setTimeout session timeout))

(defn execute-ssh
  "Execute the given command on the remote host using the configured SSH user and private key"
  [hostname command {{:keys [user private-keys private-key-password port agent-forwarding timeout]} :config}]
  (log/info "ssh" user "@" hostname command)
  (let [agent (ssh-agent {:use-system-ssh-agent false
                          :known-hosts-path     "/dev/null"})
        private-key-paths (write-private-keys private-keys)]
    (doseq [path private-key-paths]
      (add-identity agent {:private-key-path path
                           :passphrase       (some-> private-key-password
                                                     (.getBytes StandardCharsets/US_ASCII))}))
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
          (doseq [path private-key-paths]
            (io/delete-file path true)))))))


(defn ^Ssh new-ssh [config]
  (log/info "Configuring SSH with" (config/mask config))
  (map->Ssh {:config config}))
