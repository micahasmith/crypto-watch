(ns crypto.systems.gdax-new-products-monitor-test
  (:require [clojure.test :refer :all]
            [crypto.infra.helpers :refer :all]
            [crypto.systems.gdax-new-products-monitor :p]
            [clojure.core.async :as a]
            [crypto.data.gdax-redis :as gdax-redis]))



(deftest run!-tests
  (testing "can detect and install"
    (let [shutdown-chan (a/chan)]
      (with-redefs [gdax-redis/get-products (fn [& args] [])
                    gdax-redis/get-last-products (fn [& args] [])
                    gdax-redis/get-api-products (fn [& args] ["BTC/USD"])]



        )))

  )