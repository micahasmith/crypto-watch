(ns crypto.systems.binance-new-products-monitor
  (:require [crypto.infra.config :refer [get-config]]
            [crypto.infra.helpers :refer :all]
            [crypto.infra.redis :refer [redis*]]
            [crypto.infra.rolling-log :refer [roll!]]
            [crypto.services.binance :as binance]
            [crypto.data.binance-redis :as binance-redis]
            [mount.core :as mount :refer [defstate]]
            [fileape.core :as ape]
            [taoensso.timbre :as timbre :refer [info warn error]]
            [clojure.core.async :as a]
            [clojure.set :as set]
            [crypto.infra.twilio :refer [mms!]]
            [crypto.data.accounts :as accounts]
            ))




(defn -start! []
  (log-errors
    (let [shutdown-chan (a/chan)
          is-shutting-down (atom false)]
      ;; listen for shutdown
      (a/go-loop [msg (a/<! shutdown-chan)]
        (info "processing shut down")
        (reset! is-shutting-down true))

      (info "starting binance-new-products-monitor")
      (a/thread
        (while (not @is-shutting-down)
          (supressed-with
            error
            (let [main-prods (->> (binance-redis/get-products) set)
                  last-prods (binance-redis/get-last-products)
                  binance-api-prods (binance/get-products)
                  binance-api-prod-ids (->> binance-api-prods
                                            (map :symbol)
                                            set)]
              (cond
                ;; first time this has been run on this redis server... let's set it up
                (or (nil? main-prods)
                    (empty? main-prods)
                    (nil? last-prods)
                    (empty? last-prods))
                (do
                  (info "no prods,,, installing.")
                  (info binance-api-prod-ids)
                  (binance-redis/set-products! binance-api-prod-ids)
                  (binance-redis/set-last-products! binance-api-prod-ids))

                ;; we found some main-prods, has anything changed though?
                :else
                (let [new-prods (set/difference binance-api-prod-ids last-prods)]
                  (cond
                    ;; when binance has launched a new prod
                    (->> new-prods empty? not)
                    (do
                      (info "new prods!" new-prods binance-api-prod-ids)
                      ;; sync new prods to redis, publish that there was an update
                      (binance-redis/new-prods! binance-api-prod-ids new-prods)
                      ;; alert peeps
                      (->> (accounts/get-binance-accounts)
                           (run! (fn [acct]
                                   (->> acct
                                        :alert-numbers
                                        (run! (fn [num]
                                                (mms! num (str "binance: buys on " new-prods)))))
                                   ))))

                    ;; :( no new prods
                    :else
                    (info "no new prods." main-prods)
                    ))))

            ;; pause one sec
            (a/<!! (a/timeout 1100)))))

      {:shutdown-chan shutdown-chan})))

(defn -shutdown! [sys]
  (when sys
    (info sys "shutting down")
    (supressed-with warn (-> sys :shutdown-chan a/close!))))

;(def s (-start!))

;(-shutdown! s)


(defstate sys
          :start (-start!)
          :stop (-shutdown! sys))

(def sys-set #{#'crypto.infra.redis/connection
               #'crypto.systems.binance-new-products-monitor/sys})

;(def a (-run!))
;
;(-shutdown! a)



