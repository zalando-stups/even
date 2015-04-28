(ns org.zalando.stups.even.sql-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.job :refer :all]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.ssh :as ssh]))

(deftest test-from-sql
  (is (= {:foo_bar "hello"} (sql/from-sql {:tp_foo_bar "hello"}))))