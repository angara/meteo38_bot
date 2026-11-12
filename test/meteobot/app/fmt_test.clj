(ns meteobot.app.fmt-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.string :as str]
   [java-time.api :as jt]
   [meteobot.config :as config]
   [meteobot.app.fmt :as fmt]))


(use-fixtures :each
  (fn [f]
    (with-redefs [config/config {:timezone (jt/zone-id "Asia/Irkutsk")}]
      (f))))


(defn visible-length
  "Same counting as fmt/visible-length (test-side copy)."
  [html-text]
  (-> html-text
      (str/replace #"<[^>]+>" "")
      (str/replace #"&lt;|&gt;|&amp;|&quot;" "x")
      (count)))

(def ts-now (str (jt/instant)))
(def ts-old (str (jt/minus (jt/instant) (jt/hours 3))))

(defn st-data
  ([id title] (st-data id title nil))
  ([id title {:keys [fav old no-data]
              :or {fav false old false no-data false}}]
   (let [fresh (not (or old no-data))]
     {:st id
      :title title
      :is_fav fav
      :last_ts (if old ts-old ts-now)
      :last (when-not no-data
              {:t -12.4 :t_ts (if fresh ts-now ts-old) :t_delta 1.2
               :p 1013.0 :p_ts (if fresh ts-now ts-old)
               :w 2.0 :w_ts (if fresh ts-now ts-old) :g 4.0 :g_ts (if fresh ts-now ts-old)
               :b 315})})))


(deftest st-digest-combines-stations
  (let [msgs (fmt/st-digest [(st-data "irk" "Иркутск")
                             (st-data "npsd" "Николов Посад")])]
    (is (= 1 (count msgs)))
    (let [text (:text (first msgs))]
      (testing "single message gets a header"
        (is (str/starts-with? text "☀️ Погода\n\n"))
        (is (not (str/includes? text "Погода <i>"))))
      (testing "both stations present, bold titles"
        (is (str/includes? text "<b>Иркутск</b>"))
        (is (str/includes? text "<b>Николов Посад</b>")))
      (testing "blank line between stations"
        (is (str/includes? text "</a>\n\n🔹")))
      (testing "values indented with two em spaces + one space"
        (is (= "\u2003\u2003 " fmt/st-indent))
        (is (str/includes? text (str "\">" fmt/st-indent "<b>-12.4 ↑</b>"))))
      (testing "each station links to its page"
        (is (str/includes? text "https://angara.net/meteo/st/irk"))
        (is (str/includes? text "https://angara.net/meteo/st/npsd")))
      (testing "link preview disabled"
        (is (true? (get-in (first msgs) [:link_preview_options :is_disabled])))))))


(deftest st-digest-favs-context-header
  (let [text (:text (first (fmt/st-digest [(st-data "irk" "Иркутск")]
                                          {:context :favs})))]
    (is (str/starts-with? text "⭐ Избранное\n\n"))
    (is (not (str/includes? text "Избранное <i>")))))


(deftest st-digest-escapes-title
  (let [text (:text (first (fmt/st-digest [(st-data "a" "A<b>&c")])))]
    (is (str/includes? text "A&lt;b&gt;&amp;c"))))


(deftest st-digest-stale-station
  (let [text (:text (first (fmt/st-digest [(st-data "irk" "Иркутск" {:old true})
                                           (st-data "npsd" "Нет данных" {:no-data true})])))]
    (testing "stale data: warning without indent"
      (is (str/includes? text "\">⚠️ нет актуальных данных</a>"))
      (is (not (str/includes? text "⚠️ нет актуальных данных\n"))))))


(deftest st-digest-favorites-first
  (let [text (:text (first (fmt/st-digest [(st-data "b" "Байкальск")
                                           (st-data "a" "Ангарск" {:fav true})])))]
    (is (< (str/index-of text "Ангарск") (str/index-of text "Байкальск")))))


(deftest st-digest-splits-by-limit
  (let [long-title (apply str (repeat 60 "ж"))
        stations (map #(st-data (str "st" %) (str "Т" % long-title)) (range 100))
        msgs (fmt/st-digest stations)]
    (testing "more than one message"
      (is (> (count msgs) 1)))
    (testing "every message within the limit"
      (is (every? #(<= (visible-length (:text %)) fmt/digest-limit) msgs)))
    (testing "no header on split messages (prototype behavior)"
      (is (every? #(not (str/starts-with? (:text %) "☀️")) msgs)))
    (testing "all stations delivered"
      (is (= 100 (count (re-seq #"meteo/st/st\d" (str/join (map :text msgs)))))))))


(deftest st-digest-empty
  (is (= [] (fmt/st-digest []))))
