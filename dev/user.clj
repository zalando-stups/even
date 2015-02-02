(ns user
    "Tools for interactive development with the REPL. This file should
     not be included in a production build of the application."
    (:require [clojure.java.io :as io]
                [clojure.string :as str]
                [clojure.pprint :refer (pprint)]
                [clojure.repl :refer :all]
                [clojure.test :as test]
                [clojure.tools.namespace.repl :refer (refresh refresh-all)]
                [environ.core :refer [env]]
                [server.system :as system]))


(def system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system
    (constantly (system/new-system env))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (system/stop s)))))

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))