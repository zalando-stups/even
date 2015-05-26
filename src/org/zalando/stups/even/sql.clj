(ns org.zalando.stups.even.sql
  (:require [yesql.core :refer [defqueries]]
            [org.zalando.stups.friboo.system.db :refer [def-db-component generate-hystrix-commands]]
            [bugsbio.squirrel :as sq]
            [com.netflix.hystrix.core :refer [defcommand]]))

(def-db-component DB :auto-migration? true)

(def default-db-configuration
  {:db-classname "org.postgresql.Driver"
   :db-subprotocol "postgresql"
   :db-subname "//localhost:5432/even"
   :db-user "postgres"
   :db-password "postgres"
   :db-init-sql "SET search_path TO ze_data, public"})

(defqueries "db/even.sql")
(generate-hystrix-commands)

(defn strip-prefix
  "Strip the database table prefix from the given key"
  [key]
  (-> key
      name
      (.split "_")
      rest
      (#(clojure.string/join "_" %))
      keyword))

(defn from-sql
  "Transform a database result row to a valid result object: strip table prefix from column names"
  [row]
  (zipmap (map strip-prefix (keys row)) (vals row)))

; TODO still necessary since we have the cmd- wrappers?
(defcommand update-access-request-status
  "Update access request status in database"
  [handle status reason user db]
  (update-access-request! (sq/to-sql (merge handle {:status status :status-reason reason :last-modified-by user}))  {:connection db}))
