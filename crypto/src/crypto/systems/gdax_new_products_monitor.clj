(ns crypto.systems.gdax-new-products-monitor
  (:require [crypto.infra.config :refer [get-config]]
            [crypto.infra.helpers :refer :all]
            [crypto.infra.redis :refer [redis*]]
            [crypto.infra.rolling-log :refer [roll!]]
            [crypto.services.gdax :as gdax]
            [crypto.data.gdax-redis :as gdax-redis]
            [mount.core :as mount :refer [defstate]]
            [fileape.core :as ape]
            [taoensso.timbre :as timbre :refer [info warn error]]
            [clojure.core.async :as a]
            [clojure.set :as set]
            [fun-utils.core :refer [thread-seq]]
            [crypto.infra.twilio :refer [mms!]]
            [crypto.data.accounts :as accounts]
            [crypto.infra.twilio :as twilio]
            ))




(defn -start! []
  (log-errors
    (let [shutdown-chan (a/chan)
          is-shutting-down (atom false)]
      ;; listen for shutdown
      (a/go-loop [msg (a/<! shutdown-chan)]
        (info "processing shut down")
        (reset! is-shutting-down true))

      (info "starting gdax-new-products-monitor")
      (a/thread
        (while (not @is-shutting-down)
          (supressed-with
            error
            (let [main-prods (->> (gdax-redis/get-products) set)
                  last-prods (gdax-redis/get-last-products)
                  gdax-api-prods (gdax/get-usd-products)
                  gdax-api-prod-ids (->> gdax-api-prods (map :id) set)]

              (info gdax-api-prod-ids)
              (cond
                ;; first time this has been run on this redis server... let's set it up
                (or (nil? main-prods)
                    (empty? main-prods)
                    (nil? last-prods)
                    (empty? last-prods))
                (do
                  (info "no prods,,, installing.")
                  (gdax-redis/set-products! gdax-api-prod-ids)
                  (gdax-redis/set-last-products! gdax-api-prod-ids))

                ;; we found some main-prods, has anything changed though?
                :else
                (let [new-prods (set/difference gdax-api-prod-ids last-prods)]
                  (cond
                    ;; when gdax has launched a new prod
                    (->> new-prods empty? not)
                    (do
                      (info "new prods!" new-prods)
                      ;; sync new prods to redis, publish that there was an update
                      (gdax-redis/new-prods! gdax-api-prod-ids new-prods)
                      ;; alert peeps
                      (->> (accounts/get-gdax-accounts)
                           (run! (fn [acct]
                                   (->> acct
                                        :alert-numbers
                                        (run! (fn [num]
                                                (twilio/mms! num (str "gdax: buys on " new-prods)))))
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


(defstate sys
          :start (-start!)
          :stop (-shutdown! sys))

(def sys-set #{#'crypto.infra.redis/connection
               #'crypto.systems.gdax-new-products-monitor/sys})

;(def a (-start!))
;(-shutdown! a)


