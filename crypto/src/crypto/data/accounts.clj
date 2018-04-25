(ns crypto.data.accounts)

(def accounts (atom [{
                      :passcode      ""
                      :alert-numbers [""]
                      :gdax-creds    {}
                      :binance-creds {}
                      :alerts        #{}
                      }]))

(def -gdax-accounts-xf (comp (filter #(some? (-> % :gdax-creds)))))
(defn get-gdax-accounts []
  (->> @accounts
       (eduction -gdax-accounts-xf)))

(def -binance-accounts-xf (comp (filter #(some? (-> % :binance-creds)))))
(defn get-binance-accounts []
  (->> @accounts
       (eduction -gdax-accounts-xf)))


