(ns server.ssh
    (:require [clojure.tools.logging :as log]
      [clj-ssh.ssh :refer :all]))

(defrecord Ssh [config])

(defn execute-ssh [host-name command {{:keys [user private-key]} :config}]
      (log/info "ssh " user "@" host-name " " command)
      (let [agent (ssh-agent {:use-system-ssh-agent false})]
           (add-identity agent {:private-key-path private-key})
           (let [session (session agent host-name {:username user :strict-host-key-checking :no})]
                (with-connection session
                                 (let [result (ssh session {:cmd command})]
                                      (log/info "Result: " result)
                                      result)))))


(defn ^Ssh new-ssh [config]
      (log/info "Configuring SSH with" config)
      (map->Ssh {:config config}))