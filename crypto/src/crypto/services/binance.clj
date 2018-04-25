(ns crypto.services.binance
  (:require [gniazdo.core :as ws]
            [clojure.core.async :as a]
            [clojure.data.json :as json]
            [crypto.infra.helpers :refer :all]
            [crypto.infra.websockets :as websockets]
            [clj-http.client :as http]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:import (java.util Base64)
           (javax.crypto.spec SecretKeySpec)
           (java.net URL)))

(defn listen! [product-ids init-shutdown-chan to-chan]
  {
   :pre [(some? product-ids) (not-empty product-ids)]
   }
  (websockets/listen!
    (str (->> product-ids
              (map clojure.string/lower-case)
              (map #(str % "@trade"))
              (clojure.string/join "/")
              (str "wss://stream.binance.com:9443/stream?streams=")))
    10000
    init-shutdown-chan
    to-chan
    noop
    ))

(defn get-exchange-info []
  (->> (http/get "https://api.binance.com/api/v1/exchangeInfo"
                 {:as :json})
       :body))

(def -products-xf (comp
                    (filter #(some? (-> % :symbol)))
                    (filter #(or (str-contains? (-> % :symbol) "BTC")
                                 (= (-> % :symbol) "BTCUSDT")))
                    #_(map (fn [prod]
                             prod))
                    ))

(defn get-products []
  (->> (get-exchange-info)
       :symbols
       (eduction -products-xf)))


(defn get-product-book-3 [product-id]
  (info (str "binance book " product-id))
  (->> (http/get (str "https://api.binance.com/api/v1/depth?symbol=" product-id "&limit=1000")
                 {:as :json})))

(defrecord watch-books-msg [type obj ts])

(defn watch-books-3! [product-ids init-shudown-chan to-chan]
  (a/go-loop [msg (a/alt! init-shudown-chan :shutting-down
                          (a/timeout 1100) :ok)]
    (cond
      (= msg :ok)
      (do
        (info "fetching books")
        (try (->> product-ids
                  (run! (fn [id]
                          ;; pause
                          (a/<!! (a/timeout 25010))
                          ;; get data
                          (let [http-response (get-product-book-3 id)
                                status (-> http-response :status)
                                data (-> http-response :body)]
                            (if (or (= status 429) (= status 418))
                              (do
                                (warn (str "binance :( " status))
                                (a/<! (a/timeout 90000))))
                            (a/>!! to-chan (->watch-books-msg :book-3 data (now-string)))))))
             (catch Exception e (do (error e "binance books"))))
        (recur (a/alt! init-shudown-chan :shutting-down
                       (a/timeout 300000) :ok)))

      :else (info "shutting down watch-books-3"))))