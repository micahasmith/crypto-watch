(ns crypto.infra.twilio
  (:require [crypto.infra.helpers :refer :all])
  (:import (com.twilio Twilio)
           (com.twilio.rest.api.v2010.account Message)
           (com.twilio.type PhoneNumber)))

;; fill in your creds
(def sid "")
(def auth-token "")
(def from-number "")


;; init with creds
(Twilio/init sid auth-token)


(defn mms! [^String to-number ^String message]
  (log-errors
    (doto (Message/creator (PhoneNumber. to-number) (PhoneNumber. from-number) message)
      (.create))))
