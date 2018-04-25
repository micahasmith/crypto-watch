(ns crypto.infra.redis
  (:require [crypto.infra.helpers :refer :all]
            [crypto.infra.config :refer [get-config]]
            [taoensso.carmine :as car :refer (wcar)]
            [mount.core :refer [defstate]]))

; https://github.com/ptaoussanis/carmine

(defstate connection
          :start (get-config :redis))

(defmacro redis* [& body] `(car/wcar connection ~@body))


