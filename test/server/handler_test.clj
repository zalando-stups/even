(ns server.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [server.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "SSH Access Granting Service"))))

  (testing "invalid user name"
    (let [response (app (mock/request :get "/public-keys/123/sshkey.pub"))]
      (is (= (:status response) 400))
      (is (= (:body response) "Invalid user name"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
