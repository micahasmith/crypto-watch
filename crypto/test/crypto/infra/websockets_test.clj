(ns crypto.infra.websockets-test
  (:require [clojure.test :refer :all]
            [crypto.infra.helpers :refer :all]
            [crypto.infra.websockets :as websockets]
            [clojure.core.async :as a]))


(deftest start!-tests
  (testing "when create-feed-client fails it keeps retrying until shutdown is called"
      (let [{wsclose-fn :fn, wsclose-calls :calls} (spy!)
            create-feed-client-fn-count (atom 0)
            create-feed-client-fn (fn [ws-url output-chan on-connect-fn]
                                    (throw (Exception. "fake ex")))
            {cfc-fn :fn, cfc-calls :calls} (spy! create-feed-client-fn)]
        (with-redefs [websockets/create-feed-client cfc-fn
                      gniazdo.core/close wsclose-fn]
          (let [ws-url "wss://fake.com"
                init-shutdown-chan (a/chan)
                to-chan (a/chan)
                on-connect-fn noop
                _ (a/<!! (a/go-loop [_ (websockets/listen! ws-url 1000 init-shutdown-chan to-chan on-connect-fn)
                                     _ (a/<! (a/timeout 1500))]
                                    (a/close! init-shutdown-chan)))]

            (is (-> @cfc-calls count (> 1)))))))

  (testing "when the client stops receiving messages it tries to close and reopen the client"
    (let [{wsclose-fn :fn, wsclose-calls :calls} (spy!)
          create-feed-client-fn-count (atom 0)
          create-feed-client-fn (fn [ws-url output-chan on-connect-fn]
                                  (if (< @create-feed-client-fn-count 5)
                                    (do (a/go (a/>! output-chan {:type :data :obj "0"}))
                                        (swap! create-feed-client-fn-count inc))
                                    ;(loop [] (recur))
                                    (a/<!! (a/timeout 2000))
                                    ))

          {cfc-fn :fn, cfc-calls :calls} (spy! create-feed-client-fn)]
      (with-redefs [websockets/create-feed-client cfc-fn
                    gniazdo.core/close wsclose-fn]
        (let [ws-url "wss://fake.com"
              init-shutdown-chan (a/chan)
              to-chan (a/chan)
              on-connect-fn noop
              _ (a/<!! (a/go-loop [_ (websockets/listen! ws-url 1000 init-shutdown-chan to-chan on-connect-fn)
                                   _ (a/<! (a/timeout 1500))]
                                  (println "shutting down")
                                  (a/close! init-shutdown-chan)))
              to-chan-sample (a/<!! to-chan)]


          (is (-> @wsclose-calls count (> 1)))
          (is (= {:type :data :obj "0"} to-chan-sample))
          (is (-> @cfc-calls count (> 1)))))))

  )
