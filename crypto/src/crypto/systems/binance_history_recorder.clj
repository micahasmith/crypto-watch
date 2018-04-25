(ns crypto.systems.binance-history-recorder
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
            [cheshire.core :as json]
            )
  (:import (java.io File DataOutputStream)
           (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream)
           ))



(defn -start! []
  (log-errors
    (let [history-dir (get-dir-or-root (get-config :history-dir) "/logs-history/")
          prods (binance-redis/get-last-products)
          binance-output-chan (a/chan)
          binance-output-mult (a/mult binance-output-chan)
          json-encoding-xf (comp (map json/encode))
          history-writer-chan (a/chan 1024 json-encoding-xf)
          binance-websocket-shutdown-chan (a/chan)
          binance-book-3-shutdown-chan (a/chan)

          ;; send binance output into the log writer
          _ (a/tap binance-output-mult history-writer-chan)

          ;; kick off the history-writer
          history-log (roll! history-dir (str "binance-history-" (now-string) ".json") history-writer-chan)]

      ;; kick off the service
      (info "starting websocket for " prods)
      (binance/listen! prods binance-websocket-shutdown-chan binance-output-chan)
      (binance/watch-books-3! prods binance-book-3-shutdown-chan history-writer-chan)

      ;; return a map of closeable/disposable things
      {:history-log                     history-log
       :binance-output-chan             binance-output-chan
       :binance-websocket-shutdown-chan binance-websocket-shutdown-chan
       :binance-book-3-shutdown-chan    binance-book-3-shutdown-chan
       :history-writer-chan             history-writer-chan})))

(defn -shutdown! [sys]
  (log-errors
    (supressed-with warn (-> sys :binance-book-3-shutdown-chan (a/close!)))
    (supressed-with warn (-> sys :binance-websocket-shutdown-chan (a/close!)))
    (supressed-with warn (-> sys :binance-output-chan (a/close!)))
    (supressed-with warn (-> sys :history-writer-chan (a/close!)))
    (supressed-with warn (-> sys :history-log (ape/close)))))


(defn subscribe-to-changes! []
  (let [{on-next :on-next, shutdown-chan :shutdown-chan} (binance-redis/on-latest-product-change)
        system (atom nil)
        shutdown (a/chan)]
    (a/go-loop [msg (a/alt! on-next :refresh
                            shutdown :shutting-down)]
      (info "binance subscriptions" msg)
      (condp = msg
        :refresh (let [had-success?
                       (try
                         (let [next-system (-start!)
                               ;; wait 1 sec so that the next system can spin up
                               _ (a/<! (a/timeout 1000))]
                           (-shutdown! @system)
                           (reset! system next-system)
                           ;; return that we had success
                           true)
                         (catch Exception e (do
                                              (error e "during msg" msg)
                                              false)))]

                   (if had-success?
                     (do
                       (info "restart due to new prods SUCCESS")
                       (recur (a/alt! on-next :refresh
                                      shutdown :shutting-down)))
                     (do
                       (warn "restart due to new prods FAIL")
                       (recur msg))))

        :shutting-down (do
                         (supressed-with warn (a/close! shutdown))
                         (-> @system -shutdown!))))
    ;; return fn that is the closer
    (fn []
      (supressed-with warn (a/close! shutdown-chan))
      (supressed-with warn (a/close! shutdown)))))


(defstate sys
          :start (subscribe-to-changes!)
          :stop (sys))

(def sys-set #{#'crypto.infra.redis/connection
               #'crypto.systems.binance-history-recorder/sys})

;(def a (-start!))
;(-shutdown! a)


