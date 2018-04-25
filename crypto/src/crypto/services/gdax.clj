(ns crypto.services.gdax
  (:require [gniazdo.core :as ws]
            [clojure.core.async :as a]
            [clojure.data.json :as json]
            [crypto.infra.helpers :refer :all]
            [crypto.infra.websockets :as websockets]
            [clj-http.client :as http]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:import (java.util Base64)
           (javax.crypto.spec SecretKeySpec)
           (java.net URL)))

;; https://github.com/weissjeffm/gdax-bot/blob/master/src/coinbase_api/core.clj

;(def ws-url "wss://ws-feed-public.sandbox.exchange.coinbase.com")
;(def ws-url "wss://ws-feed.exchange.coinbase.com")
(def ^:private ws-url "wss://ws-feed.gdax.com")
;(def api-url "https://api-public.sandbox.exchange.coinbase.com")
(def ^:private api-url "https://api.gdax.com")

;;
;; websocket shiiiiiiit
;;

(defn listen! [product-ids init-shutdown-chan to-chan]
  {
   :pre [(some? product-ids) (not-empty product-ids)]
   }
  (websockets/listen! ws-url
                      10000
                      init-shutdown-chan
                      to-chan
                      (fn [conn]
                        (ws/send-msg conn (json/write-str {:type "subscribe" :gdax-api/product_ids product-ids}))
                        (ws/send-msg conn (json/write-str {:type "heartbeat" :on true}))
                        (ws/send-msg conn (json/write-str {:type "matches" :on true}))

                        )))




;;
;; REST af
;;

(defonce ^:private hmac (javax.crypto.Mac/getInstance "HmacSHA256"))

(defn ^:private encode [bs]
  (-> (Base64/getEncoder) (.encodeToString bs)))

(defn ^:private decode [s]
  (-> (Base64/getDecoder) (.decode s)))

(defn ^:private sign [key message]
  (.init hmac (SecretKeySpec. key "HmacSHA256"))
  (.doFinal hmac message))

(defn ^:private wrap-coinbase-auth [client]
  (fn [req]
    (let [sk (-> req :CB-ACCESS-SECRET decode)
          timestamp (format "%f" (/ (System/currentTimeMillis) 1000.0))
          sign-message (str timestamp
                            (-> req :method name .toUpperCase)
                            (-> req :url (URL.) .getPath)
                            (:body req))
          headers {:CB-ACCESS-KEY        (:CB-ACCESS-KEY req)
                   :CB-ACCESS-SIGN       (->> sign-message .getBytes (sign sk) encode)
                   :CB-ACCESS-TIMESTAMP  timestamp
                   :CB-ACCESS-PASSPHRASE (:CB-ACCESS-PASSPHRASE req)}]
      (client (update-in req [:headers] merge headers)))))

(defmacro ^:private with-coinbase-auth [& body]
  `(http/with-middleware (conj http/default-middleware #'wrap-coinbase-auth)
                         ~@body))

(def ^:dynamic *credentials*)

(defn -get [url]
  (with-coinbase-auth
    (http/get url *credentials*)))

(defn -post [url body]
  (with-coinbase-auth
    (http/post url (assoc *credentials* :body body :content-type :json))))

(defn ^:private url [path]
  (format "%s%s" api-url path))

(defn get-products []
  (->> (http/get (str api-url "/products") {:as :json})
       :body))

(def ^:private -usd-xf (comp (filter #(= (-> % :quote_currency) "USD"))))
(defn get-usd-products []
  (->> (get-products)
       (eduction -usd-xf)))

(defn get-product-book-2 [id]
  (->> (http/get (str api-url "/products/" id "/book?level=2") {:as :json})
       :body))

(defn get-product-book-3 [id]
  (info "fecthing book" id)
  (->> (http/get (str api-url "/products/" id "/book?level=3") {:as :json})
       :body))

(defrecord watch-books-msg [type obj ts])

(defn watch-books-3! [product-ids init-shudown-chan to-chan]
  (a/go-loop [msg (a/alt! init-shudown-chan :shutting-down
                          (a/timeout 1100) :ok)]
    (cond
      (= msg :ok)
      (do
        (info "fetching books")
        (try (->> product-ids
                  (map (fn [id]
                         ;; pause
                         (a/<!! (a/timeout 2000))
                         ;; get data
                         (get-product-book-3 id)))
                  (run! #(a/>!! to-chan (->watch-books-msg :book-3 % (now-string)))))
             (catch Exception e (do (error e "gdax books"))))
        (recur (a/alt! init-shudown-chan :shutting-down
                       (a/timeout 60000) :ok)))
      :else (info "shutting down watch-books-3"))))

;(get-product-book "BTC-USD")

