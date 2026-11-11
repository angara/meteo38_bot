(ns meteobot.app.command-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [jsonista.core :as json]
   [meteobot.app.command :as command]
   [meteobot.config :as config]
   [meteobot.data.store :as store]))


(use-fixtures :each
  (fn [f]
    (with-redefs [config/build-info (constantly {:appname "meteo38_bot"
                                               :version "2.1.56"
                                               :commit "c0322cd"})]
      (f))))


(defn- api-request-recorder [requests]
  (fn [req]
    (swap! requests conj
           {:method (last (str/split (:url req) #"/"))
            :params (json/read-value (:body req) json/keyword-keys-object-mapper)})
    {:status 200 :body "{\"ok\":true,\"result\":{}}"}))


(deftest favs-command-preserves-station-cards-without-extra-message
  (let [requests (atom [])
        station-data {"irk" {:st "irk" :title "Иркутск" :descr "Центр" :last nil :last_ts nil}
                      "npsd" {:st "npsd" :title "Николов Посад" :descr "Посад" :last nil :last_ts nil}}
        replacements {(ns-resolve 'meteobot.data.store 'user-favs) (constantly ["irk" "npsd"])
                      (ns-resolve 'meteobot.data.store 'station-info) #(get station-data %)
                      (ns-resolve 'mlib.telegram.botapi 'execute-request) (api-request-recorder requests)}]
    (with-redefs-fn replacements
      #(command/route-command {:apikey "test"} {:chat {:id 123}} "/favs"))
    (is (= 2 (count @requests)) "only the two station cards")
    (doseq [[request station] (map vector (take 2 @requests) ["Иркутск" "Николов Посад"])]
      (is (= "sendMessage" (:method request)))
      (is (re-find (re-pattern station) (get-in request [:params :text])))
      (is (seq (get-in request [:params :reply_markup :inline_keyboard]))))))


(deftest inline-keyboard-command-has-no-followup
  (let [requests (atom [])
        replacements {(ns-resolve 'meteobot.data.store 'station-info)
                      (constantly {:st "irk" :lat 52.27 :lon 104.27 :title "Иркутск"})
                      (ns-resolve 'mlib.telegram.botapi 'execute-request) (api-request-recorder requests)}]
    (with-redefs-fn replacements
      #(command/route-command {:apikey "test"} {:chat {:id 123}} "/map irk"))
    (is (= 1 (count @requests)))
    (testing "the map response retains its inline station button"
      (is (= "sendLocation" (:method (first @requests))))
      (is (seq (get-in (first @requests) [:params :reply_markup :inline_keyboard]))))))


(deftest subs-command-shows-main-keyboard-with-or-without-subscriptions
  (doseq [subscriptions [[] [{:st "irk" :subs_id 42
                              :hhmm (java.time.LocalTime/of 8 0) :wdays "12345"}]]]
    (let [requests (atom [])]
      (with-redefs-fn
        {(ns-resolve 'mlib.telegram.botapi 'execute-request) (api-request-recorder requests)
         #'store/user-subs (constantly subscriptions)
         #'store/station-info (constantly {:title "Иркутск"})}
        #(command/route-command {:apikey "test"} {:chat {:id 123}} "/subs"))
      (is (= 1 (count @requests)))
      (let [{:keys [method params]} (first @requests)]
        (is (= "sendMessage" method))
        (is (= "HTML" (:parse_mode params)))
        (is (true? (get-in params [:reply_markup :resize_keyboard])))
        (is (str/includes? (:text params)
                           (if (seq subscriptions) "/sub_42" "Чтобы настроить рассылку")))))))


(deftest help-keeps-the-main-keyboard
  (let [requests (atom [])]
    (with-redefs-fn {(ns-resolve 'mlib.telegram.botapi 'execute-request) (api-request-recorder requests)}
      #(command/route-command {:apikey "test"} {:chat {:id 123}} "/help"))
    (is (= 1 (count @requests)))
    (is (str/ends-with? (get-in (first @requests) [:params :text])
                       "\n\n<code>- meteo38_bot 2.1.56 c0322cd</code>"))
    (is (true? (get-in (first @requests) [:params :reply_markup :resize_keyboard])))))


(defn- search-fixture [requests f]
  (with-redefs-fn
    {(ns-resolve 'mlib.telegram.botapi 'execute-request) (api-request-recorder requests)
     #'store/get-user-location (constantly nil)
     #'store/user-favs (constantly [])}
    f))

(def search-stations
  (mapv (fn [i] {:st (str "st" i) :title (str "Станция " i) :descr "Иркутск" :distance 100})
        (range 12)))

(deftest text-search-pages-five-stations-and-edits-the-same-message
  (let [requests (atom [])
        msg {:chat {:id 123}}]
    (search-fixture requests
      #(with-redefs [store/search-active-stations (constantly search-stations)]
         (is (true? (command/route-text {:apikey "test"} msg " Иркутск ")))
         (let [params (:params (first @requests))
               next-data (get-in params [:reply_markup :inline_keyboard 0 0 :callback_data])]
           (is (= 5 (count (re-seq #"/info_" (:text params)))))
           (is (str/includes? (:text params) "страница 1/3"))
           (is (<= (alength (.getBytes ^String next-data "UTF-8")) 64))
           (command/route-callback {:apikey "test"}
                                   {:id "cb" :message (assoc msg :message_id 456) :data next-data})
           (let [edited (second @requests)]
             (is (= "editMessageText" (:method edited)))
             (is (= 456 (get-in edited [:params :message_id])))
             (is (= 5 (count (re-seq #"/info_" (get-in edited [:params :text])))))
             (is (str/includes? (get-in edited [:params :text]) "страница 2/3"))
             (let [last-page (get-in edited [:params :reply_markup :inline_keyboard 0 1 :callback_data])]
               (command/route-callback {:apikey "test"}
                                       {:id "cb2" :message (assoc msg :message_id 456) :data last-page})
               (let [last-edit (nth @requests 3)]
                 (is (= 2 (count (re-seq #"/info_" (get-in last-edit [:params :text])))))
                 (is (str/includes? (get-in last-edit [:params :text]) "страница 3/3"))))))))))


(deftest single-page-search-has-main-keyboard
  (doseq [station-count [1 5]]
    (let [requests (atom [])]
      (search-fixture requests
        #(with-redefs [store/search-active-stations (constantly (take station-count search-stations))]
           (command/route-text {:apikey "test"} {:chat {:id 123}} "Иркутск")))
      (is (= 1 (count @requests)))
      (is (= station-count
             (count (re-seq #"/info_" (get-in (first @requests) [:params :text])))))
      (is (true? (get-in (first @requests) [:params :reply_markup :resize_keyboard])))
      (is (nil? (get-in (first @requests) [:params :reply_markup :inline_keyboard]))))))


(deftest short-text-shows-minimum-length-and-main-keyboard
  (doseq [text ["" "  " "а" "аб" " аб "]]
    (let [requests (atom [])]
      (search-fixture requests
        #(with-redefs [store/search-active-stations (fn [& _] (throw (ex-info "unexpected search" {})))]
           (is (true? (command/route-text {:apikey "test"} {:chat {:id 123}} text)))))
      (is (= 1 (count @requests)))
      (is (= "Для поиска введите не менее 3 символов."
             (get-in (first @requests) [:params :text])))
      (is (true? (get-in (first @requests) [:params :reply_markup :resize_keyboard]))))))


(deftest non-text-and-unknown-commands-do-not-search
  (with-redefs [store/search-active-stations (fn [& _] (throw (ex-info "unexpected search" {})))]
    (doseq [text [nil "/unknown" "!unknown"]]
      (is (nil? (command/route-text {} {:chat {:id 123}} text))))))


(deftest empty-search-includes-inline-actions-and-escaped-query
  (let [requests (atom [])]
    (search-fixture requests
      #(with-redefs [store/search-active-stations (constantly [])]
         (command/route-text {:apikey "test"} {:chat {:id 123}} "<&>")))
    (let [params (:params (first @requests))]
      (is (= 1 (count @requests)))
      (is (str/includes? (:text params) "активных станций не найдено"))
      (is (str/includes? (:text params) "&lt;&amp;&gt;"))
      (is (= ["search_active" "search_help"]
             (mapv :callback_data (get-in params [:reply_markup :inline_keyboard 0])))))))


(deftest empty-search-actions-open-help-or-active-stations
  (let [requests (atom [])
        callback {:id "cb" :message {:chat {:id 123} :message_id 456}}]
    (search-fixture requests
      #(with-redefs [command/active-stations-for-location (constantly (take 2 search-stations))]
         (command/route-callback {:apikey "test"} (assoc callback :data "search_active"))
         (command/route-callback {:apikey "test"} (assoc callback :data "search_help"))))
    (is (= ["answerCallbackQuery" "sendMessage" "answerCallbackQuery" "sendMessage"]
           (mapv :method @requests)))
    (is (= 2 (count (re-seq #"/info_" (get-in (second @requests) [:params :text])))))
    (is (true? (get-in (last @requests) [:params :reply_markup :resize_keyboard])))))


(deftest expired-or-foreign-search-is-rejected
  (let [requests (atom [])
        token (store/save-search 123 "Иркутск" search-stations)]
    (search-fixture requests
      #(doseq [[chat-id query-token] [[999 token] [123 "expired"]]]
         (command/route-callback {:apikey "test"}
                                 {:id "cb" :message {:chat {:id chat-id} :message_id 1}
                                  :data (str "search:" query-token ":1")})))
    (is (= ["answerCallbackQuery" "answerCallbackQuery"] (mapv :method @requests)))
    (is (every? #(str/includes? (get-in % [:params :text]) "устарел") @requests))))
