(defproject crypto "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :main crypto.core
  :plugins [[lein-environ "1.1.0"]]
  :dep {}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.3.465"]
                 [org.clojure/data.json "0.2.6"]
                 [camel-snake-kebab "0.4.0"]
                 [clj-time "0.14.2"]

                 ;logging
                 [com.taoensso/timbre "4.10.0"]


                 ;websockets, etc
                 [stylefruits/gniazdo "1.0.1"]
                 [clj-http "3.7.0"]

                 ;state
                 [mount "0.1.11"]

                 ;rolling file dumps
                 [fileape "1.1.0"]
                 [org.apache.commons/commons-compress "1.15"]

                 ;redis
                 [com.taoensso/carmine "2.17.0"]

                 ;retrys
                 [com.brunobonacci/safely "0.3.0"]

                 ;handle signals
                 [spootnik/signal "0.2.1"]

                 ;env settings
                 [environ "1.1.0"]

                 ;json
                 [cheshire "5.8.0"]

                 ;async utils mostly
                 [fun-utils "0.6.2"]

                 ;twilio
                 [com.twilio.sdk/twilio "7.17.0"]

                 ]
  :profiles {
             :uberjar {
                            :env {
                                  :redis {:host "127.0.0.1" :port 6379 }
                                  :history-dir nil
                                  :log-dir nil
                                  }
                            }

             :dev          {
                            :env {
                                  :redis {:host "127.0.0.1" :port 6379 }
                                  :history-dir nil
                                  :log-dir nil
                                  }
                            :dependencies [
                                           ;sl4j support
                                           ;[com.fzakaria/slf4j-timbre "0.3.8"]
                                           ;[org.slf4j/log4j-over-slf4j "1.7.14"]
                                           ;[org.slf4j/jul-to-slf4j "1.7.14"]
                                           ;[org.slf4j/jcl-over-slf4j "1.7.14"]
                                           ;[org.slf4j/slf4j-api "1.7.25"]
                                           ]
                            }
             }
  )
