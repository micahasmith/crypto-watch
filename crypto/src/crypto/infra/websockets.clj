(ns crypto.infra.websockets
  (:require [gniazdo.core :as ws]
            [clojure.core.async :as a]
            [clojure.data.json :as json]
            [safely.core :refer [safely]]
            [clojure.spec.alpha :as spec]
            [crypto.infra.helpers :refer :all]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]))

(defrecord websocket-msg [type obj ts])

(defn create-feed-client
  [ws-url output-chan on-connect-fn]
  (info "connecting to" ws-url)
  (let [conn (ws/connect ws-url
                         :on-receive (fn [^String v]
                                       ;(debug v)
                                       (a/put! output-chan
                                                             (->websocket-msg :data v (now-string))
                                                             ))
                         :on-error (fn [^Throwable e]
                                     (error e "ws error" ws-url)
                                     (a/put! output-chan (->websocket-msg :data nil (now-string)))
                                     )
                         :on-close (fn [code desc]
                                     (info "closing on" ws-url code desc)
                                     (a/put! output-chan
                                             (->websocket-msg :close [code desc] (now-string)))))]
    (on-connect-fn conn)
    conn))

(defn listen! [ws-url ws-timeout init-shutdown-chan to-chan on-connect-fn]
  (let [heartbeat-chan (a/chan (a/sliding-buffer 1))
        websocket-output-chan (a/chan 4096)
        websocket-mult (a/mult websocket-output-chan)
        is-shutting-down (atom false)]

    ;; publish out to subscribers
    (a/tap websocket-mult to-chan)
    ;; monitor that we're getting messages to heartbeat-chan
    (a/tap websocket-mult heartbeat-chan)

    ;; allow short circuiting if websocket never pans out
    (a/go-loop [msg (a/<! init-shutdown-chan)]
      (reset! is-shutting-down true))

    (a/go
      ;; loop forever regardless of websocket errors, only exiting on shutdown-chan signal
      (while (not @is-shutting-down)
        (info "restarting websocket listener loop")
        (supressed-with
          error
          ;; make our websocket client
          (let [websocket (create-feed-client ws-url websocket-output-chan on-connect-fn)]
            ;; loop forever, monitoring that we're receiving messages and don't want to shut down
            (loop [status (a/alt! heartbeat-chan :ok
                                  (a/timeout ws-timeout) :timed-out)]
              (cond
                @is-shutting-down (do
                                    (info "shut down called!")
                                    ;; safely close the websocket if still open
                                    (supressed-with warn (ws/close websocket)))

                ;; if we've received a msg in the last 10sec, keep looping
                (= :ok status) (recur (a/alt! heartbeat-chan :ok
                                              (a/timeout ws-timeout) :timed-out
                                              ))

                ;; if we've timed out
                :else (do
                        (error :error "timed out!")
                        ;; safely close the websocket if still open
                        (supressed-with warn (ws/close websocket)))
                )))))

      )))