(ns server.ssh
    (:require [clojure.tools.logging :as log]
      [clj-ssh.ssh :refer :all]))


(defn execute-ssh [host-name command {:keys [ssh-user ssh-private-key]}]
      (log/info "ssh " ssh-user "@" host-name " " command)
      (let [agent (ssh-agent {:use-system-ssh-agent false})]
           (add-identity agent {:private-key-path ssh-private-key})
           (let [session (session agent host-name {:username ssh-user :strict-host-key-checking :no})]
                (with-connection session
                                 (let [result (ssh session {:cmd command})]
                                      (log/info "Result: " result)
                                      result)))))
