(ns meteobot.app.command
  (:require
   [clojure.string :as str]
   [taoensso.telemere :refer [log!]]
   [mlib.telegram.botapi :as botapi]
   [meteobot.data.store :as store]
   [meteobot.app.fmt :as fmt]
   ,))


(def ^:const ACTIVE_HOURS 3)

(def ^:const ACTIVE_PAGE_SIZE 4)
(def ^:const NEAR_PAGE_SIZE 3)

(def ^:const LAT_0 52.27)
(def ^:const LNG_0 104.27)


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
  
  ())




(defn stinfo [cfg {{chat-id :id} :chat} st]
  (if-let [stinfo (store/station-info st)]
    (let [favs (store/user-favs chat-id)
          msg (-> (fmt/st-info stinfo)
                  (assoc :reply_markup (fmt/kbd-fav-subs st (favs st))))]
      (botapi/send-message cfg chat-id msg))
    (botapi/send-html cfg chat-id 
               (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn stmap [cfg {chat :chat} st]
  (if-let [stinfo (store/station-info st)]
    (botapi/send-location cfg (:id chat) 
                   {:latitude (:lat stinfo) :longitude (:lon stinfo)
                    :reply_markup {:inline_keyboard
                                   [[{:text (str "📈 " (:title stinfo)) 
                                      :url (fmt/meteo-st-link st)}]]}})
    (botapi/send-html cfg (:id chat) (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn start [cfg {chat :chat :as msg} st]
  (botapi/send-message cfg (:id chat)
                {:text "start text stub\n\nTODO: full version"
                 :reply_markup fmt/main-buttons})
  (when st
    (stinfo cfg msg st)))


(defn help [cfg {chat :chat} _]
  (botapi/send-message cfg (:id chat) 
                {:text "help text"
                 :reply_markup fmt/main-buttons
                 }))


; - - - - - - - - - -

(defn active-stations-for-location [location]
  (when location
    (store/active-stations (:latitude location) (:longitude location) ACTIVE_HOURS)))


(defn set-inline-keyboard [cfg chat-id msg-id kbd]
  (botapi/send cfg :editMessageReplyMarkup {:chat_id chat-id :message_id msg-id :reply_markup kbd}))


(defn clear-keyboard [cfg chat-id msg-id]
  (botapi/send cfg :editMessageReplyMarkup {:chat_id chat-id :message_id msg-id :reply_markup {}}))


(defn next-keyboard [prefix offset]
  {:inline_keyboard [[{:text "Ещё..." :callback_data (str prefix "_next:" offset)}]]})


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
             (str "<b>Активные станции</b>\n" 
                  "📌 " (fmt/float6 latitude) " " (fmt/float6 longitude)))
  (let [st-list (active-stations-for-location location)
        [page rest] (split-at NEAR_PAGE_SIZE st-list)
        more-kbd (when (seq rest) (next-keyboard "near" NEAR_PAGE_SIZE))
        ]
    (doseq [msg (next-messages page fmt/st-info more-kbd)]
      (botapi/send-message cfg chat-id msg))
    ,))


(defn cb-near-next [cfg 
                    {{{chat-id :id} :chat msg-id :message_id} :message :as cbk} 
                    [_ offset & _]]
  (if-let [n (parse-long offset)]
    (let [location (store/get-user-location chat-id)
          acts (drop n (active-stations-for-location location))
          page (take NEAR_PAGE_SIZE acts)
          rest (drop NEAR_PAGE_SIZE acts)
          more-kbd (when (seq rest) (next-keyboard "near" (+ n NEAR_PAGE_SIZE)))
          ]
      (when-not location
        (log! ["cb-next-near: user location missing"]))
      
      (clear-keyboard cfg chat-id msg-id)

      (doseq [msg (next-messages page fmt/st-info more-kbd)]
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


(defn active [cfg {{chat-id :id} :chat} _]
  (let [location (or (store/get-user-location chat-id) {:latitude LAT_0 :longitude LNG_0})
        [page rest] (split-at ACTIVE_PAGE_SIZE (active-stations-for-location location))
        kbd-more (when (seq rest) (next-keyboard "active" ACTIVE_PAGE_SIZE))
        ]
    (->> 
     (active-list-message page kbd-more)
     (botapi/send-message cfg chat-id)
     ,)))


(defn cb-active-next [cfg
                      {{{chat-id :id} :chat msg-id :message_id} :message :as cbk}
                      [_ offset & _]]
    (if-let [n (parse-long offset)]
      (let [location (or (store/get-user-location chat-id) {:latitude LAT_0 :longitude LNG_0})
            st-list (active-stations-for-location location)
            [page rest] (->> st-list (drop n) (split-at ACTIVE_PAGE_SIZE))
            kbd-more (when (seq rest) (next-keyboard "active" (+ n ACTIVE_PAGE_SIZE)))]
        (clear-keyboard cfg chat-id msg-id)
        (->>
         (active-list-message page kbd-more)
         (botapi/send-message cfg chat-id)))        
      ;;      
      (log! :warn ["cb-active-next: invalid offset" cbk])
      ))


; - - - - - - - - - -

(defn favs [cfg {{chat-id :id} :chat :as msg} _]
  (doseq [st (store/user-favs chat-id)]
    (stinfo cfg msg st)))


(defn cb-fav [cfg 
              {{{chat-id :id} :chat msg-id :message_id} :message :as cbk}
              [_ add-del st & _]
              ]
  (tap> [add-del st])
  (case add-del
    "add" (store/user-fav-add chat-id st)
    "del" (store/user-fav-del chat-id st)
    (log! :warn ["cb-fav: wrong add-del parameter" cbk]))
  (let [favs (store/user-favs chat-id)]
    (tap> ["favs:" favs])
    (set-inline-keyboard cfg chat-id msg-id (fmt/kbd-fav-subs st (favs st)))
    ))


; - - - - - - - - - -

(def command-map
  {"start"  start
   "help"   help
   "info"   stinfo
   "map"    stmap
   "favs"   favs
   "active" active
   ,})


(defn route-command [cfg msg text]
  (when-let [[cmd param] (parse-command text)]
    (when-let [hc (get command-map cmd)]
      (hc cfg msg param)
      true
      )))


(defn route-text [cfg msg text]
  (condp = text
    fmt/BTN_FAVS_TEXT (favs cfg msg nil)
    nil))


(defn route-location [cfg msg location]
  (when location
    (on-location cfg msg location)
    true
    ))


(def callback-map
  {
   "near_next" #'cb-near-next
   "active_next" #'cb-active-next
   "fav" #'cb-fav
   "subs" prn ;; XXX:!!!
   ,})


(defn route-callback [cfg {data :data :as cbk}]
  (let [params (str/split data #":")]
    (when-let [h-cbk (get callback-map (first params))]
      (h-cbk cfg cbk params)
      true)
    ))


(def MENU_COMMANDS 
  [
   {:command "active" :description "Актвные станции"}
   {:command "favs"   :description "Избранное"}
   {:command "subs"   :description "Подписки"}
   {:command "help"   :description "Краткая справка"}
   ])


(defn setup-menu-commands [cfg]
  (botapi/set-my-commands cfg MENU_COMMANDS nil nil))
