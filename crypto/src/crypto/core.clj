(ns crypto.core
  (:require [crypto.infra.config :refer [get-config]]
            [crypto.infra.helpers :refer :all]
            [mount.core :as mount]
            [signal.handler :refer [with-handler]]
            [taoensso.timbre :as timbre :refer [info error]]
            [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
            [crypto.systems.core :as systems])
  (:gen-class))


(defn -main [& args]
  (if (some? args)
    (if-let [arg-map (get-arg-map args)]
      (let [config (-> arg-map :config keyword)]
        (info "setting config as " config)
        (if (-> config systems/profiles some?)
          (do
            (systems/start! config arg-map)
            ;; block
            (@(promise)))
          (throw (IllegalArgumentException. "Wrong config string")))))))



;;
;; init stuff
;;
;; https://github.com/ptaoussanis/timbre
(timbre/merge-config!
  {:appenders
   {:spit (rolling-appender {:path (-> (or (get-config :log-dir)
                                            (str (.getCanonicalPath (clojure.java.io/file ".")) "/logs/"))
                                        (str "log.txt"))})}})
(timbre/merge-config!
  {:appenders
   {:spit (rolling-appender {:min-level :warn
                             :path (-> (or (get-config :log-dir)
                                           (str (.getCanonicalPath (clojure.java.io/file ".")) "/logs/"))
                                       (str "errors.txt"))})}})

;; catch unhandled java errrrrrrrs
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (error ex "Uncaught exception on" (.getName thread)))))

;;
;; signals
;;

;; https://github.com/pyr/signal
;; https://people.cs.pitt.edu/~alanjawi/cs449/code/shell/UnixSignals.htm

;; SIGINT (interrupt/quit)
(with-handler :int
              (info "caught SIGINT, quitting.")
              (systems/stop!)
              (shutdown-agents)
              (System/exit 0))

(with-handler :term
              (info "caught SIGTERM, quitting.")
              (systems/stop!)
              (shutdown-agents)
              (System/exit 0))

(with-handler :hup
              (info "caught SIGHUP, reloading.")
              (systems/stop!)
              (systems/start!))