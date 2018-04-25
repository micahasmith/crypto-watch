(ns crypto.infra.config
  (:require [environ.core :refer [env]]
            [crypto.infra.helpers :refer :all]
            ))

(def ^:dynamic *key-namespace* "")

(defn get-config [config-key]
  (let [val (-> config-key env)]
    (if (str-nil-or-empty? val)
      nil
      val)))

(defn namespace-key [stringg]
  (str (or (get-config :namespace)
           *key-namespace*)) stringg)
