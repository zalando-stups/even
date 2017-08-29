(ns org.zalando.stups.even.ssh
  (:require [clojure.tools.logging :as log]
            [clj-ssh.ssh :refer :all]
            [clojure.java.io :as io]
            [org.zalando.stups.friboo.config :as config]
            [com.netflix.hystrix.core :refer [defcommand]]
            [clojure.string :as str]
            [com.stuartsierra.component :as component])
  (:import
    [java.nio.file.attribute PosixFilePermissions]
    [java.nio.file.attribute FileAttribute]
    [java.nio.file Files]
    (java.util UUID Base64)
    (java.nio.charset StandardCharsets)))

(defrecord Ssh [config]
  component/Lifecycle
  (start [component]
    (let [decoded-keys (as-> (:private-keys config) keys
                             (str/trim keys)
                             (.decode (Base64/getDecoder) keys)
                             (String. keys StandardCharsets/US_ASCII)
                             (str/split keys #"(?=-----BEGIN)")
                             (map str/trim keys)
                             (filter not-empty keys))]
      (assoc component :private-keys decoded-keys)))
  (stop [component]
    (dissoc component :private-keys)))

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

(defn set-timeout [session timeout]
  (.setTimeout session timeout))

(defn execute-ssh
  "Execute the given command on the remote host using the configured SSH user and private key"
  [hostname command {{:keys [user private-key-password port agent-forwarding timeout]} :config private-keys :private-keys}]
  (log/info "ssh" user "@" hostname command)
  (let [agent (ssh-agent {:use-system-ssh-agent false
                          :known-hosts-path     "/dev/null"})
        private-key-paths (doall (map write-key-to-file private-keys))]
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
