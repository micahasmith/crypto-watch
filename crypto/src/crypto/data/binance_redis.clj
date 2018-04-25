(ns crypto.data.binance-redis
  (:require [crypto.infra.helpers :refer :all]
            [crypto.infra.config :refer [get-config namespace-key]]
            [crypto.infra.redis :refer [redis* connection]]
            [taoensso.carmine :as car :refer (wcar)]
            [clojure.core.async :as a]
            [taoensso.timbre :as timbre :refer [info warn error]]))


(defonce PRODUCT_SET_KEY "binance_products")                   ;
(defonce BINANCE_LATEST_SET_KEY "binance_latest_products")
(defonce PRODUCTS_TO_BUY_SET_KEY "binance_buy_products")
(defonce BUYS_IN_PROG_SET_KEY "binance_buy_inprog_products")
(defonce PRODUCTS_TO_SELL_SET_KEY "binance_sell_products")
(defonce BINANCE_LATEST_PRODUCTS_PUBSUB "binance_latest_products_pubsub")
(defonce BINANCE_BUYS_PUBSUB "binance_buy_pubsub")

(defn get-products []
  (redis* (car/smembers (namespace-key PRODUCT_SET_KEY))))

(defn set-products! [products]
  (redis* (apply (partial car/sadd (namespace-key PRODUCT_SET_KEY)) products)))

(defn set-last-products! [products]
  (redis* (apply (partial car/sadd (namespace-key BINANCE_LATEST_SET_KEY)) products)))

(defn get-last-products []
  (redis* (car/smembers (namespace-key BINANCE_LATEST_SET_KEY))))

(defn get-buys []
  (redis* (car/smembers (namespace-key PRODUCTS_TO_BUY_SET_KEY))))

(defn new-prods!
  "Sync to redis a new set of products than previously available, including prods to buy via `buy-prods`."
  [last-prods buy-prods]
  (redis*
    ;; set latest prods on binance
    (apply (partial car/sadd (namespace-key BINANCE_LATEST_SET_KEY)) last-prods)
    ;; notify/pub that we got a new latest
    (car/publish (namespace-key BINANCE_LATEST_PRODUCTS_PUBSUB) last-prods)
    ;; add the new prod[s] to the buy set
    (apply (partial car/sadd (namespace-key PRODUCTS_TO_BUY_SET_KEY)) buy-prods)
    ;; notify/pub that we got buys
    (car/publish (namespace-key BINANCE_LATEST_PRODUCTS_PUBSUB) buy-prods)
    ))

;(get-last-products)

(defn on-latest-product-change []
  (let [on-next (a/chan)
        shutdown-chan (a/chan)]
    (redis*
      (let [listener (car/with-new-pubsub-listener
                       (:spec connection)
                       {BINANCE_LATEST_PRODUCTS_PUBSUB (fn f1 [msg]
                                                      (a/>!! on-next msg))}
                       (info (str "subscribing " BINANCE_LATEST_PRODUCTS_PUBSUB))
                       (car/subscribe BINANCE_LATEST_PRODUCTS_PUBSUB)
                       )]
        (a/go-loop [_ (a/<! shutdown-chan)]
          (info (str "closing listener " BINANCE_LATEST_PRODUCTS_PUBSUB))
          (car/close-listener listener))))
    {:on-next       on-next
     :shutdown-chan shutdown-chan}))