(ns crypto.systems.core
  (:require [crypto.infra.config :refer [get-config]]
            [crypto.infra.helpers :refer :all]
            [mount.core :as mount]
            [taoensso.timbre :as timbre :refer [info]]
            [crypto.systems.gdax-history-recorder :as gdax-history-recorder]
            [crypto.systems.gdax-new-products-monitor :as gdax-new-products-monitor]
            [crypto.systems.binance-history-recorder :as binance-history-recorder]
            [crypto.systems.binance-new-products-monitor :as binance-new-products-monitor]))


(def profiles {
               :gdax-history-recorder gdax-history-recorder/sys-set
               :gdax-new-products-monitor gdax-new-products-monitor/sys-set
               :binance-history-recorder binance-history-recorder/sys-set
               :binance-new-products-monitor binance-new-products-monitor/sys-set
               })

(def this-system (atom nil))
(def start-args (atom nil))

(defn start!
  ([sys-key & args]
   {
    :pre [(-> sys-key profiles some?)]
    }
   (when (and (some? args)
              (not-empty args)
              (coll? (first args)))
     (reset! start-args (first args)))
   (reset! this-system sys-key)
   (mount/start (->> profiles sys-key mount/only)))
  ([]
   (start! @this-system)))

(defn stop!
  ([sys-key]
   (mount/stop (->> profiles sys-key mount/only)))
  ([]
   (stop! @this-system)))

;(start! :gdax-history-recorder)

;(stop! :gdax-history-recorder)
;
;(start! :gdax-new-products-monitor)
;
;(stop! :gdax-new-products-monitor)

