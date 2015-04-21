(ns org.zalando.stups.even.core-test
    (:require
      [clojure.test :refer :all]
      [org.zalando.stups.even.core :refer :all]))

(deftest test-system-map
         (is (map? (new-system {}))))


