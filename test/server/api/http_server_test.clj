(ns server.api.http-server-test
    (:require [clojure.test :refer :all]
      [com.stuartsierra.component :refer [start stop]]
      [server.api.http-server :as http-server]
      [server.api.router :as router]
      [clj-http.client :as client]))

(defn- server
       ([]
         (server {}))
       ([options]
         (assoc (http-server/new-http-server options)
                :router (router/new-router))))

(deftest test-http-server-restart
         (let [started (start (server))]
              (start started)
              (stop started)))

(deftest test-http-server-stop-twice
         (let [started (start (server))]
              (stop started)
              (stop started)))

(deftest test-http-server-stop-unstarted
         (stop (server)))

(deftest test-http-server-health
         (let [started (start (server {}))]
              (is (= 200 (:status (client/get "http://localhost:8080/health"))))
              (stop started)))

(deftest test-http-server-port
         (is (= 8081 (:port (server {:port "8081"})))))
