(ns meteobot.app.command
  (:require
   [clojure.string :as str]
   [taoensso.telemere :refer [log!]]
   [mlib.telegram.botapi :refer [send send-message send-html send-location set-my-commands]]
   [meteobot.data.store :refer 
    [station-info active-stations set-user-location get-user-location]]
   [meteobot.app.fmt :as fmt :refer [main-buttons BTN_FAVS_TEXT]]
   ,))


(def ^:const ACTIVE_HOURS 3)

(def ^:const ACTIVE_PAGE_SIZE 10)

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


(defn stinfo [cfg {chat :chat} st]
  (if-let [stinfo (station-info st)]
    (send-message cfg (:id chat) (fmt/st-info stinfo))
    (send-html cfg (:id chat) (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn stmap [cfg {chat :chat} st]
  (if-let [stinfo (station-info st)]
    (send-location cfg (:id chat) 
                   {:latitude (:lat stinfo) :longitude (:lon stinfo)
                    :reply_markup {:inline_keyboard
                                   [[{:text (str "📈 " (:title stinfo)) 
                                      :url (fmt/meteo-st-link st)}]]}})
    (send-html cfg (:id chat) (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn start [cfg {chat :chat :as msg} st]
  (send-message cfg (:id chat)
                {:text "start text"
                 :reply_markup main-buttons})
  (when st
    (stinfo cfg msg st)))


(defn help [cfg {chat :chat} _]
  (send-message cfg (:id chat) 
                {:text "help text"
                 :reply_markup main-buttons
                 }))


; - - - - - - - - - -

(defn active-stations-for-location [location]
  (when location
    (active-stations (:latitude location) (:longitude location) ACTIVE_HOURS)))


(defn near-next-keyboard [offset]
  {:inline_keyboard [[{:text "Ещё..." :callback_data (str "near_next:" offset)}]]})


(defn on-location [cfg {{chat-id :id} :chat} {:keys [latitude longitude] :as location}]
  (log! ["location:" {:chat-id chat-id :location location}])
  (set-user-location chat-id location)
  (send-html cfg chat-id
             (str "<b>Активные станции</b>\n" 
                  "📌 " (fmt/float6 latitude) " " (fmt/float6 longitude)))
  (let [st-list (active-stations-for-location location)
        page0 (take ACTIVE_PAGE_SIZE st-list)
        rest  (drop ACTIVE_PAGE_SIZE st-list)
        ]
    (doseq [stinfo page0]
      (send-message cfg chat-id (fmt/st-info stinfo)))
    (when (seq rest)
      (send-message cfg chat-id 
                    {:text "near_next" :reply_markup (near-next-keyboard ACTIVE_PAGE_SIZE)
                     }))
      ,))


(defn cb-near-next [cfg 
                    {{{chat-id :id} :chat msg-id :message_id} :message :as cbk} 
                    [_ offset & _]]
  (tap> ["cb-near-next:" cbk offset])
  (if-let [n (parse-long offset)]
    
    (let [location (get-user-location chat-id)
          acts (drop n (active-stations-for-location location))
          page (take ACTIVE_PAGE_SIZE acts)
          rest (drop ACTIVE_PAGE_SIZE acts)
          ]
      (when-not location
        (log! ["cb-next-near: user location missing"]))
      
      ;; XXX:
      ;; hide button
      ; chat_id , message_id
      ; inline_message_id
      (prn "--- rc:"
      (send cfg :editMessageReplyMarkup {:chat_id chat-id 
                                         :message_id msg-id
                                         ;:inline_message_id (-> cbk :id)
                                         :reply_markup {}})) 

      (doseq [stinfo page]
        (send-message cfg chat-id (fmt/st-info stinfo)))
      (when (seq rest)
        (send-message cfg chat-id
                      {:text "near_next" 
                       :reply_markup (near-next-keyboard (+ n ACTIVE_PAGE_SIZE))})
        )
      )
    (log! :warn ["cb-near-next: invalid offset" cbk])
    )
  ;;
  )


; - - - - - - - - - -      

(defn favs [cfg msg _]
  (let [favs-list ["uiii" "istok" "npsd" "botanika7" "olha" "olha2"]]
    (doseq [st favs-list]
      (stinfo cfg msg st)
      )
    )
  )


(defn active [cfg {chat :chat} _]
  (let [sts (active-stations LAT_0 LNG_0 ACTIVE_HOURS)]
    (doseq [st sts]
      (send-message cfg (:id chat) (fmt/st-info st))
      )
    )
  )


(def command-map
  {"start"  start
   "help"   help
   "stinfo" stinfo
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
    BTN_FAVS_TEXT (favs cfg msg nil)
    nil))


(defn route-location [cfg msg location]
  (when location
    (on-location cfg msg location)
    true
    ))


(def callback-map
  {"near_next" #'cb-near-next})


(defn route-callback [cfg {data :data :as cbk}]
  (let [params (str/split data #":")]
    (when-let [h-cbk (get callback-map (first params))]
      (h-cbk cfg cbk params)
      true)
    ))


(def MENU_COMMANDS 
  [
   {:command "help"   :description "Краткая справка"}
   {:command "active" :description "Актвные станции"}
   {:command "favs"   :description "Избранное"}
   {:command "subs"   :description "Подписки"}
   ])


(defn setup-menu-commands [cfg]
  (set-my-commands cfg MENU_COMMANDS nil nil))
