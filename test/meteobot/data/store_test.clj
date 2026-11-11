(ns meteobot.data.store-test
  (:require
   [clojure.core.cache.wrapped :as cache]
   [clojure.test :refer [deftest is]]
   [meteobot.data.store :as store]))


(deftest search-matches-active-station-title-or-description-without-case
  (let [stations [{:st "a" :title "ИРКУТСК" :descr nil}
                  {:st "b" :title nil :descr "Посёлок Листвянка"}
                  {:st "c" :title "Братск" :descr "Центр"}]
        calls (atom [])]
    (with-redefs [store/active-stations (fn [& args] (swap! calls conj args) stations)]
      (is (= ["a"] (mapv :st (store/search-active-stations " иркутск " 52 104 3))))
      (is (= ["b"] (mapv :st (store/search-active-stations "ЛИСТВЯНКА" 52 104 3))))
      (is (= [] (store/search-active-stations "не существует" 52 104 3)))
      (is (every? #(= [52 104 3] %) @calls)))))


(deftest latest-search-replaces-previous-search-and-is-chat-scoped
  (with-redefs [store/search-results* (cache/ttl-cache-factory {} :ttl 600000)]
    (let [first-token (store/save-search 123 "первый" [{:st "a"}])
          second-token (store/save-search 123 "второй" [{:st "b"}])]
      (is (nil? (store/get-search 123 first-token)))
      (is (nil? (store/get-search 999 second-token)))
      (is (= "второй" (:query (store/get-search 123 second-token))))
      (is (= [{:st "b"}] (:stations (store/get-search 123 second-token)))))))
