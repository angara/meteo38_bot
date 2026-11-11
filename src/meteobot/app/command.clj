(ns meteobot.app.command
  (:require
   [clojure.string :as str]
   [taoensso.telemere :refer [log!]]
   [mlib.telegram.botapi :as botapi]
   [meteobot.data.store :as store]
   [meteobot.config :as config]
   [meteobot.app.fmt :as fmt] 
   [meteobot.app.subs :as subs]
   ,))


(def ^:const ACTIVE_HOURS 3)

(def ^:const ACTIVE_PAGE_SIZE 5)
(def ^:const NEAR_PAGE_SIZE 3)

(def DEFAULT_LOCATION 
  {:latitude 52.27 :longitude 104.27})


(def START_TEXT
  (str
   "Вы подключились к @meteo38bot.\n"
   "Для получения краткой инструкции используйте команду /help."
   ))


(def HELP_TEXT
  (str
   "🔸 Этот бот предоставляет информацию с сети автоматических метеостанций"
   " <a href=\"https://meteo38.ru\">meteo38.ru</a>"
   " и возможность настроить рассылку уведомлений в указанное время по данным сайта.\n\n"
   "🔸 По кнопке '<b>⭐ Избранное</b>' выводятся последние данные с выбранных метеостанций.\n\n"
   "🔸 Кнопка '<b>🌍 Рядом</b>' использует функцию геолокации для поиска ближайших станций.\n\n"
   "🔸 Введите не менее трёх символов для поиска активных станций по названию и описанию.\n\n"
   "🔸 Из '<b>Меню</b>' можно воспользоваться остальными командами и управлять рассылками.\n\n"
   "📩 Пожелания по работе бота пишите в группу - @meteo38\n"
   ,))


(def EMPTY_FAVS 
  (str "🔸 Чтобы добавить станцию в избранное\n\n"
       "- зайдите в список станций /active или /near\n"
       "- перейдите на информацию о станции по ссылке 'ℹ️ info_'\n"
       "- нажмите на кнопку ➕."))


(defn parse-command [s]
  (when s
    (when-let [[_ cmd param] (re-matches #"^[/!]([A-Za-z0-9]+)[ _]*(.*)$" s)]
      [cmd (if (str/blank? param) nil param)]
      )))


(comment
  
  (parse-command "/start test_123 456")
  ;;=> ["start" "test_123 456"]
  
  (parse-command "")
  ;;=> nil
  
  (parse-command "qwe 123")
  ;;=> nil
  
  (parse-command "/start ")
  ;;=> ["start" nil]
  
  (parse-command "!hello 1 2 3")
  ;;=> ["hello" "1 2 3"]
  
  ,)


(defn stinfo [cfg {{chat-id :id} :chat} {st :param}]
  (if-let [st-data (store/station-info st)]
    (let [favs (set (store/user-favs chat-id))
          msg (fmt/st-info st-data 
                           {:show-info-link false 
                            :reply-markup (fmt/kbd-fav-subs st (favs st))})
          ]
      (botapi/send-message cfg chat-id msg))
    (botapi/send-html cfg chat-id 
                      (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn cmd-map [cfg {chat :chat} {st :param}]
  (if-let [stinfo (store/station-info st)]
    (botapi/send-location cfg (:id chat) 
                   {:latitude (:lat stinfo) :longitude (:lon stinfo)
                    :reply_markup {:inline_keyboard
                                   [[{:text (str "📈 " (:title stinfo)) 
                                      :url (fmt/meteo-st-link st)}]]}})
    (botapi/send-html cfg (:id chat) (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


; - - - - - - - - - -

(defn cmd-start [cfg {chat :chat :as msg} {st :param}]
  (botapi/send-message cfg (:id chat)
                {:text START_TEXT
                 :parse_mode "HTML"
                 :reply_markup fmt/main-buttons})
  (when st
    (stinfo cfg msg {:param st})))


(defn cmd-help [cfg {{chat-id :id} :chat} _]
  (let [{:keys [appname version commit]} (config/build-info)]
    (botapi/send-message cfg chat-id
                         {:text (str (str/trimr HELP_TEXT) "\n\n<code>- "
                                     (botapi/hesc (str appname " " version " " commit))
                                     "</code>")
                          :parse_mode "HTML"
                          :reply_markup fmt/main-buttons})))


; - - - - - - - - - -

(defn set-fav-flag [st-list favs]
  (map #(if (favs (:st %)) (assoc % :is-fav true) %) st-list))


(defn active-stations-for-location [location]
  (when location
    (store/active-stations (:latitude location) (:longitude location) ACTIVE_HOURS)))


(defn next-keyboard [prefix offset]
  {:inline_keyboard [[{:text "Ещё..." :callback_data (str prefix "_next:" offset)}]]})


(defn prev-next-keyboard [prefix prev-page next-page]
  (let [btns [(when prev-page
                {:text "⬅️" :callback_data (str prefix ":" prev-page)})
              (when next-page
                {:text "➡️" :callback_data (str prefix ":" next-page)})]]
    {:inline_keyboard [(remove nil? btns)]}))


(defn next-messages [st-list format-fn kbd-more]
  (if kbd-more
    (let [btn-idx (dec (count st-list))]
      (->> st-list
           (map-indexed 
            (fn [i st]
              (cond-> (format-fn st)
                (= i btn-idx) (assoc :reply_markup kbd-more)
                ,)))))
    (map format-fn st-list)))


(defn on-location [cfg {{chat-id :id} :chat} {:keys [latitude longitude] :as location}]
  (log! ["location:" {:chat-id chat-id :location location}])
  (store/set-user-location chat-id location)
  (botapi/send-html cfg chat-id
             (str "📌 " (fmt/float6 latitude) " " (fmt/float6 longitude)))
  (let [st-list (active-stations-for-location location)
        [page rest] (split-at NEAR_PAGE_SIZE st-list)
        more-kbd (when (seq rest) (next-keyboard "near" NEAR_PAGE_SIZE))
        favs (set (store/user-favs chat-id))
        ]
    (doseq [msg (next-messages (set-fav-flag page favs) 
                               #(fmt/st-info % nil) 
                               more-kbd)]
      (botapi/send-message cfg chat-id msg))
    ,))


(defn cmd-near [cfg {{chat-id :id} :chat :as msg} _]
  (let [location (or (store/get-user-location chat-id) 
                     DEFAULT_LOCATION)]
    (on-location cfg msg location)))


(defn cb-near-next [cfg 
                    {{{chat-id :id} :chat msg-id :message_id} :message :as cbk} 
                    [_ offset]]
  (if-let [n (parse-long offset)]
    (let [location (store/get-user-location chat-id)
          acts (drop n (active-stations-for-location location))
          page (take NEAR_PAGE_SIZE acts)
          rest (drop NEAR_PAGE_SIZE acts)
          more-kbd (when (seq rest) (next-keyboard "near" (+ n NEAR_PAGE_SIZE)))
          favs (set (store/user-favs chat-id))
          ]
      (when-not location
        (log! ["cb-next-near: user location missing"]))
      
      (botapi/edit-reply-markup cfg chat-id msg-id {})
      
      (doseq [msg (next-messages (set-fav-flag page favs) 
                                 #(fmt/st-info % nil) 
                                 more-kbd)]
        (botapi/send-message cfg chat-id msg))
      ,)
    ;;
    (log! :warn ["cb-near-next: invalid offset" cbk])
    ))


; - - - - - - - - - -

(defn active-list-message [st-list kbd-more]
  {:text (str/join "\n" (map fmt/active-list-item st-list))
   :parse_mode "HTML"
   :link_preview_options {:is_disabled true}
   :reply_markup (or kbd-more fmt/main-buttons)
   ,})


(defn cmd-active [cfg {{chat-id :id} :chat} _]
  (let [location (or (store/get-user-location chat-id) 
                     DEFAULT_LOCATION)
        [page rest] (split-at ACTIVE_PAGE_SIZE (active-stations-for-location location))
        kbd (when (seq rest) (prev-next-keyboard "active" nil 1))
        favs (set (store/user-favs chat-id))
        ]
    (->> 
     (active-list-message (set-fav-flag page favs) kbd)
     (botapi/send-message cfg chat-id)
     ,)))


(defn cb-active [cfg
                 {{{chat-id :id} :chat msg-id :message_id} :message}
                 [_ page-num]]
  (let [pgn (or (parse-long page-num) 0)
        offset (* ACTIVE_PAGE_SIZE pgn)
        location (or (store/get-user-location chat-id)
                     DEFAULT_LOCATION)
        st-list (active-stations-for-location location)
        [page rest] (->> st-list (drop offset) (split-at ACTIVE_PAGE_SIZE))
        kbd (prev-next-keyboard "active"
                                (when (> pgn 0) (dec pgn))
                                (when (seq rest) (inc pgn)))
        favs (set (store/user-favs chat-id))]
    (->>
     (active-list-message (set-fav-flag page favs) kbd)
     (botapi/edit-message cfg chat-id msg-id))
    ,))


; - - - - - - - - - -

(defn cmd-favs [cfg {{chat-id :id} :chat :as msg} opts]
  (if-let [favs (seq (store/user-favs chat-id))]
    (doseq [st favs]
      (stinfo cfg msg (assoc opts :param st)))
    (botapi/send-html cfg chat-id EMPTY_FAVS)))


(defn cb-fav [cfg 
              {{{chat-id :id} :chat msg-id :message_id} :message cbk-id :id :as cbk}
              [_ add-del st]
              ]
  (case add-del
    "add" (when-let [err-msg (:error (store/user-fav-add chat-id st))]
            (botapi/answer-callback-text cfg cbk-id err-msg))
    "del" (store/user-fav-del chat-id st)
    (log! :warn ["cb-fav: wrong add-del parameter" cbk]))
  (let [favs (set (store/user-favs chat-id))]
    (botapi/edit-reply-markup cfg chat-id msg-id (fmt/kbd-fav-subs st (favs st)))
    ))


(defn favs-brief [cfg {{chat-id :id} :chat}]
  (if-let [st-list (seq (store/user-favs chat-id))]
    (let [st-datas (keep #(store/station-info %) st-list)]
      ;; one digest message instead of a message per favorite station
      (doseq [msg (fmt/st-digest st-datas {:context :favs})]
        (botapi/send-message cfg chat-id msg)))
    (botapi/send-html cfg chat-id EMPTY_FAVS)))


; - - - - - - - - - -

(def command-map
  {"start"  cmd-start
   "help"   cmd-help
   "info"   stinfo
   "near"   cmd-near
   "map"    cmd-map
   "favs"   cmd-favs
   "sub"    subs/cmd-subs-edit
   "subs"   subs/cmd-subs
   "active" cmd-active
   ,})


(defn route-command [cfg msg text]
  (when-let [[cmd param] (parse-command text)]
    (when-let [hc (get command-map cmd)]
      (hc cfg msg {:param param})
      true)))


(def search-empty-keyboard
  {:inline_keyboard [[{:text "Активные станции" :callback_data "search_active"}
                      {:text "Помощь" :callback_data "search_help"}]]})

(defn- search-page-message [chat-id token {:keys [query stations]} page-num]
  (let [page-count (max 1 (long (Math/ceil (/ (count stations) (double ACTIVE_PAGE_SIZE)))))
        page-num (max 0 (min page-num (dec page-count)))
        page (take ACTIVE_PAGE_SIZE (drop (* page-num ACTIVE_PAGE_SIZE) stations))
        favs (set (store/user-favs chat-id))
        keyboard (prev-next-keyboard (str "search:" token)
                                    (when (pos? page-num) (dec page-num))
                                    (when (< page-num (dec page-count)) (inc page-num)))]
    {:text (str "🔎 Поиск: <b>" (botapi/hesc query) "</b>\n"
                "Найдено: " (count stations) ", страница " (inc page-num) "/" page-count "\n\n"
                (str/join "\n" (map fmt/active-list-item (set-fav-flag page favs))))
     :parse_mode "HTML"
     :link_preview_options {:is_disabled true}
     :reply_markup (if (> page-count 1) keyboard fmt/main-buttons)}))

(defn search-stations [cfg {{chat-id :id} :chat} query]
  (let [location (or (store/get-user-location chat-id) DEFAULT_LOCATION)
        stations (store/search-active-stations query (:latitude location) (:longitude location) ACTIVE_HOURS)]
    (if (seq stations)
      (let [token (store/save-search chat-id query stations)]
        (botapi/send-message cfg chat-id
                             (search-page-message chat-id token {:query query :stations stations} 0)))
      (botapi/send-message cfg chat-id
                           {:text (str "По запросу «" (botapi/hesc query) "» активных станций не найдено.")
                            :parse_mode "HTML"
                            :reply_markup search-empty-keyboard}))))

(defn cb-search [cfg {{{chat-id :id} :chat msg-id :message_id} :message cbk-id :id}
                 [_ token page-num]]
  (if-let [result (store/get-search chat-id token)]
    (if-let [page (when page-num (parse-long page-num))]
      (do
        (botapi/edit-message cfg chat-id msg-id (search-page-message chat-id token result page))
        (botapi/answer-callback-text cfg cbk-id ""))
      (botapi/answer-callback-text cfg cbk-id "Некорректная страница."))
    (botapi/answer-callback-text cfg cbk-id "Поиск устарел. Введите запрос ещё раз.")))

(defn cb-search-active [cfg {msg :message cbk-id :id} _]
  (botapi/answer-callback-text cfg cbk-id "")
  (cmd-active cfg msg nil))

(defn cb-search-help [cfg {msg :message cbk-id :id} _]
  (botapi/answer-callback-text cfg cbk-id "")
  (cmd-help cfg msg nil))

(defn route-text [cfg msg text]
  (cond
    (= text fmt/BTN_FAVS_TEXT)
    (do (favs-brief cfg msg) true)

    (and (string? text)
         (not (re-find #"^[/!]" (str/trim text))))
    (do
      (if (>= (count (str/trim text)) 3)
        (search-stations cfg msg (str/trim text))
        (botapi/send-message cfg (get-in msg [:chat :id])
                             {:text "Для поиска введите не менее 3 символов."
                              :reply_markup fmt/main-buttons}))
      true)

    :else nil))


(defn route-location [cfg msg location]
  (when location
    (on-location cfg msg location)
    true
    ))


(def callback-map
  {
   "near_next" cb-near-next
   "active"    cb-active
   "search"    cb-search
   "search_active" cb-search-active
   "search_help" cb-search-help
   "fav"       cb-fav
   "subs_new"  subs/cb-subs-new
   "subs"      subs/cb-subs
   ,})


(defn route-callback [cfg {data :data :as cbk}]
  (let [params (str/split data #":")]
    (when-let [h-cbk (get callback-map (first params))]
      (h-cbk cfg cbk params)
      true)
    ))


(def MENU_COMMANDS 
  [
   {:command "active" :description "Активные станции"}
   {:command "favs"   :description "Избранное"}
   {:command "subs"   :description "Рассылки"}
   {:command "near"   :description "Рядом"}
   {:command "help"   :description "Краткая справка"}
   ])


(defn setup-menu-commands [cfg]
  (botapi/set-my-commands cfg MENU_COMMANDS nil nil))
