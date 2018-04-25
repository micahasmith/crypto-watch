(ns crypto.data.gdax-redis
  (:require [crypto.infra.helpers :refer :all]
            [crypto.infra.config :refer [get-config namespace-key]]
            [crypto.infra.redis :refer [redis* connection]]
            [taoensso.carmine :as car :refer (wcar)]
            [clojure.core.async :as a]
            [taoensso.timbre :as timbre :refer [info warn error]]))


(defonce PRODUCT_SET_KEY "gdax_products")                   ;
(defonce GDAX_LATEST_SET_KEY "gdax_latest_products")
(defonce PRODUCTS_TO_BUY_SET_KEY "gdax_buy_products")
(defonce BUYS_IN_PROG_SET_KEY "gdax_buy_inprog_products")
(defonce PRODUCTS_TO_SELL_SET_KEY "gdax_sell_products")
(defonce GDAX_LATEST_PRODUCTS_PUBSUB "gdax_latest_products_pubsub")
(defonce GDAX_BUYS_PUBSUB "gdax_buy_pubsub")

(defn get-products []
  (redis* (car/smembers (namespace-key PRODUCT_SET_KEY))))

(defn set-products! [products]
  (redis* (apply (partial car/sadd (namespace-key PRODUCT_SET_KEY)) products)))

(defn set-last-products! [products]
  (redis* (apply (partial car/sadd (namespace-key GDAX_LATEST_SET_KEY)) products)))

(defn get-last-products []
  (redis* (car/smembers (namespace-key GDAX_LATEST_SET_KEY))))

(defn get-buys []
  (redis* (car/smembers (namespace-key PRODUCTS_TO_BUY_SET_KEY))))

(defn new-prods!
  "Sync to redis a new set of products than previously available, including prods to buy via `buy-prods`."
  [last-prods buy-prods]
  (redis*
    ;; set latest prods on gdax
    (apply (partial car/sadd (namespace-key GDAX_LATEST_SET_KEY)) last-prods)
    ;; notify/pub that we got a new latest
    (car/publish (namespace-key GDAX_LATEST_PRODUCTS_PUBSUB) last-prods)
    ;; add the new prod[s] to the buy set
    (apply (partial car/sadd (namespace-key PRODUCTS_TO_BUY_SET_KEY)) buy-prods)
    ;; notify/pub that we got buys
    (car/publish (namespace-key GDAX_LATEST_PRODUCTS_PUBSUB) buy-prods)
    ))

;(get-last-products)

(defn on-latest-product-change []
  (let [on-next (a/chan)
        shutdown-chan (a/chan)]
    (redis*
      (let [listener (car/with-new-pubsub-listener
                       (:spec connection)
                       {GDAX_LATEST_PRODUCTS_PUBSUB (fn f1 [msg]
                                                      (a/>!! on-next msg))}
                       (info (str "subscribing " GDAX_LATEST_PRODUCTS_PUBSUB))
                       (car/subscribe GDAX_LATEST_PRODUCTS_PUBSUB)
                       )]
        (a/go-loop [_ (a/<! shutdown-chan)]
          (info (str "closing listener " GDAX_LATEST_PRODUCTS_PUBSUB))
          (car/close-listener listener))))
    {:on-next       on-next
     :shutdown-chan shutdown-chan}))