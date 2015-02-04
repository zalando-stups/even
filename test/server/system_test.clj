(ns server.system-test
    (:require
      [clojure.test :refer :all]
      [server.system :refer :all]))

(deftest test-system-map
         (is (map? (new-system {}))))


