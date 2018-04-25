(ns crypto.infra.rolling-log
  (:require [crypto.infra.config :refer [get-config]]
            [crypto.infra.helpers :refer :all]
            [fileape.core :as ape]
            [taoensso.timbre :as timbre :refer [info warn]]
            [clojure.core.async :as a]
            [fun-utils.core :as fun-utils]
            )
  (:import (java.io File DataOutputStream)
           (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream)
           ))

;; https://github.com/gerritjvv/fileape/blob/master/src/fileape/core.clj

(def ^:private nl "\n")

(defn roll! [roll-to-dir file-name write-chan]
  (let [history-log (ape/ape {:codec            :gzip
                              :base-dir         roll-to-dir
                              :check-freq       10000
                              :rollover-size    10000000
                              :rollover-timeout 60000})]
    (a/go-loop [^String dat (a/<! write-chan)]
      (when dat
        (log-errors
          (ape/write history-log
                     file-name
                     (fn [{:keys [^DataOutputStream out]}]
                       (.writeBytes out (str dat nl))))))
      (recur (a/<! write-chan)))
    history-log))