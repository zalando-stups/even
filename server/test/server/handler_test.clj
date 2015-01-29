(ns server.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [server.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "SSH Access Granting Service"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
