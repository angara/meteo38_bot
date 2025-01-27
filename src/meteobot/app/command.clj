(ns meteobot.app.command
  (:require
   [clojure.string :as str]
   [mlib.telegram.botapi :refer [send-text send-message send-html send-location]]
   [meteobot.data.store :refer [station-info]]
   [meteobot.app.fmt :as fmt :refer [main-buttons BTN_FAVS_TEXT]]
   ,))


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
    (send-html cfg (:id chat)
               (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


(defn stmap [cfg {chat :chat} st]
  (if-let [stinfo (station-info st)]
    (send-location cfg (:id chat) 
                   {:latitude (:lat stinfo) :longitude (:lon stinfo)
                    :reply_markup
                    [[{:text (:title st) :url (fmt/meteo-st-link st)}]]})
    (send-html cfg (:id chat)
               (str "Станция не найдена!\n⚠️ <b>" st "</b>"))))


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


(defn on-location [cfg msg location]
  (prn "location:" location) ;; XXX: !!!
  
  )




(defn favs [cfg msg _]
  (let [favs-list ["uiii" "istok" "npsd" "botanika7" "olha" "olha2"]]
    (doseq [st favs-list]
      (stinfo cfg msg st)
      )
    )
  )



(def command-map
  {"start"  start
   "help"   help
   "stinfo" stinfo
   "map"    stmap
   "favs"   favs
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
