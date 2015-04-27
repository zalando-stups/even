(ns org.zalando.stups.even.sql
  (:require [yesql.core :refer [defqueries]]
            [org.zalando.stups.friboo.system.db :refer [def-db-component]]
            [bugsbio.squirrel :as sq]))

(def-db-component DB :auto-migration? true)

(def default-db-configuration
  {:db-classname "org.postgresql.Driver"
   :db-subprotocol "postgresql"
   :db-subname "//localhost:5432/even"
   :db-user "postgres"
   :db-password "postgres"})

(defqueries "db/even.sql")

(defn update-access-request-status
  "Update access request status in database"
  [handle status reason user db]
  (update-access-request! (sq/to-sql (merge handle {:status status :status-reason reason :last-modified-by user}))  {:connection db}))