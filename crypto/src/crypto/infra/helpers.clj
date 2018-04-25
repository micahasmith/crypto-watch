(ns crypto.infra.helpers
  (:require [clojure.data.json :as json]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [clj-time.core :as time]
            [clj-time.coerce :as timecoerce]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [camel-snake-kebab.core :refer [->kebab-case-keyword ->snake_case ->kebab-case ->PascalCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [taoensso.timbre :as timbre :refer [info warn error]]
            [clojure.test :refer [deftest testing is]])
  (:import (org.joda.time DateTime)))

;;
;; startup
;;

(extend-type DateTime
  json/JSONWriter
  (-write [date out]
    (json/-write (timecoerce/to-string date) out)))


;;
;; utilities
;;

(defn get-dir-or-root [dir root-modifier]
  (or dir
      (str (.getCanonicalPath (clojure.java.io/file ".")) root-modifier)))

(defn str-contains? [^String strng ^String sub-strng]
  (cond
    (nil? strng) false
    (nil? sub-strng) false
    :else (.contains strng sub-strng)))

(defn str-not-contains? [^String strng ^String sub-strng]
  (not (str-contains? strng sub-strng)))

(defn now-string []
  (->> (t/now) (f/unparse (f/formatter "yyyyMMddHHmmssSSS"))))

(defmacro supressed [& body]
  `(try
     (do ~@body)
     (catch Exception e#
       nil)))


(defmacro supressed-with [ex-fn & body]
  `(try
     (do ~@body)
     (catch Exception e#
       (~ex-fn e#))))

(defmacro log-errors
  "Logs errors and rethrows"
  [& body]
  `(try
     (do ~@body)
     (catch Exception e#
       (error e#)
       (throw e#))))

(defmacro fn->error-wrap
  "Wraps a function so that it returns :error if an error happens"
  [& body]
  `(try
     (do ~@body)
     (catch Exception e# :error)))

;(fn->error-wrap (throw (Exception. "fuck")))

(defn json-read
  "Read a json string. Returns :error if it can't read it."
  ([json]
   (fn->error-wrap
     (json/read-str json :key-fn ->kebab-case-keyword)))
  ([json key-fn]
   (fn->error-wrap
     (json/read-str json :key-fn key-fn))))


(defn json-write [obj]
  (if-not (nil? obj)
    (json/write-str obj)))

(defn json-read-str [s]
  (json/read-str s :key-fn keyword))

(def noop (fn [& args] nil))

(defn edn-read
  "Read an edn string. Returns :error if it can't read it."
  [edn]
  (fn->error-wrap
    (edn/read-string edn)))

(defn edn-write
  [obj]
  (prn-str obj))

(defn filter-first [pred coll]
  (->> coll (filter pred) first))

(defn string==
  "Case invariant string equality check."
  [string1 string2]
  (cond
    (and (nil? string1) (nil? string2)) true
    (or (nil? string1) (nil? string2)) false
    :else (= (string/lower-case string1) (string/lower-case string2))))

(defn map->str
  "Takes a map, sorts it by keys, and then retruns a string containing its values."
  [map]
  (->> (into (sorted-map) map)
       (reduce (fn [^String r ^String i] (str r "-" (i 0) "=" (i 1))) "")))

(defn prun!
  "Like pmap except doesnt care about results."
  [fun coll]
  (doall (pmap (fn [i] (fun i) nil) coll))
  nil)

(defn map->snake-keys [mapp]
  (transform-keys ->snake_case mapp))

(defn map->kebab-keys [mapp]
  (transform-keys ->kebab-case mapp))

(defn map->pascal-keys [mapp]
  (transform-keys ->PascalCase mapp))

(defn str-nil-or-empty? [strr]
  (cond
    (nil? strr) true
    (empty? strr) true
    :else false))

;;
;; core.-main args
;;

(defn get-key-val [arg]
  (as-> arg x
        (clojure.string/split x #":")
        {(keyword (get x 0)) (get x 1)}))


(defn get-arg-map [args]
  (reduce #(merge %1 (get-key-val %2)) {} args))

;;
;; patterns
;;

;(defmacro async-looper)


;;
;; testing
;;

(defn spy!
  ([fake-fn]
   (let [calls (atom [])
         fake-fnn (if (some? fake-fn) fake-fn (fn [& args] nil))
         fnn (fn [& args]

               (swap! calls conj (vec args))
               (apply fake-fnn args))]
     {:calls calls
      :fn    fnn}))
  ([] (spy! nil)))

#_(deftest -spy-tests
         (testing "can collect results"
                  (let [spy (spy!)]
                    (is (-> spy :calls deref empty?))

                    ;; call 1
                    ((-> spy :fn) :hello :world)
                    (is (-> spy :calls deref (get 0) (= [:hello :world])))

                    ;; call 2
                    ((-> spy :fn) :how :are-you)
                    (is (-> spy :calls deref (get 0) (= [:hello :world])))
                    (is (-> spy :calls deref (get 1) (= [:how :are-you])))
                    ))

         (testing "can be a fake"
                  (let [spy (spy! str)]
                    (is (-> spy :calls deref empty?))

                    ;; call 1
                    (let [result-1 ((-> spy :fn) :hello :world)]
                      (is (-> spy :calls deref (get 0) (= [:hello :world])))
                      (is (= result-1 ":hello:world"))

                      ;; call 2
                      (let [result-2 ((-> spy :fn) :how :are-you)]
                        (is (-> spy :calls deref (get 0) (= [:hello :world])))
                        (is (-> spy :calls deref (get 1) (= [:how :are-you])))
                        (is (= result-2 ":how:are-you"))
                        )))))