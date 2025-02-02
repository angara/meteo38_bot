(ns meteobot.app.fmt
  (:import 
   [java.util Locale]
   [java.text NumberFormat]
   )
  (:require
   [clojure.string :as str]
   [java-time.api :as jt]
   [mlib.telegram.botapi :refer [hesc]]
   [meteobot.config :refer [config]]
   ,))


(set! *warn-on-reflection* true)


(def SEQN* (atom 0))

;; NOTE: used to ensure that the kbd is not identical.
(defn next-seq []
  (swap! SEQN* inc))


(defn meteo-st-link [st-id]
  (str "https://angara.net/meteo/st/" st-id))

; - - - - - - - - - -

(def BTN_FAVS_TEXT "⭐️ Избранное")

(def BTN_NEAR_TEXT "🌍 Рядом")


(def main-buttons
  {:is_persistent true
   :resize_keyboard true
   :keyboard [[{:text BTN_FAVS_TEXT}
               {:text BTN_NEAR_TEXT
                :request_location true}]]})

; - - - - - - - - - -

(def tf-hhmm (jt/formatter "HH:mm"))

;(def tf-ddmmyyyy (jt/formatter "dd.MM.yyyy"))

(def tf-ddmmyyyy_hhmm (jt/formatter "dd.MM.yyyy HH:mm"))


(defn hpa-mmhg [h]
  (when h 
    (/ h 1.3332239)))


(defn wind-rhumb [b]
  (when (and b (<= 0 b) (< b 360))
    (let [rh (int (mod (Math/floor (/ (+ b 22) 45)) 8))]
      (["С" "СВ" "В" "ЮВ" "Ю" "ЮЗ" "З" "СЗ"] rh))))

(comment
  (wind-rhumb 0)
  ;;=> "С"
  
  (wind-rhumb 23)
  ;;=> "СВ"

  (wind-rhumb 359)
  ;;=> "С"

  (wind-rhumb 337)
  ;;=> "СЗ"

  ())


(defn ts->dt [ts]
  (-> (jt/instant ts)
      (jt/zoned-date-time (:timezone config))
      (jt/local-date-time)
      ))


(defn last-dt [dt]
  (if (jt/before? dt (jt/minus (jt/local-date-time) (jt/hours 12)))
    (jt/format tf-ddmmyyyy_hhmm dt)
    (jt/format tf-hhmm dt)))


(defn float1 [x]
  (->
   (doto (NumberFormat/getNumberInstance Locale/ROOT)
     (.setMinimumFractionDigits 0)
     (.setMaximumFractionDigits 1)
     (.setGroupingUsed false))
   (.format x)))


(defn float1plus [x]
  (if (> x 0)
    (str "+" (float1 x))
    (float1 x)))


(defn float6 [x]
  (->
   (doto (NumberFormat/getNumberInstance Locale/ROOT)
     (.setMinimumFractionDigits 1)
     (.setMaximumFractionDigits 6)
     (.setGroupingUsed false))
   (.format x)))


(comment
  
  (float1 1.0)
  ;;=> "1"
  
  (float1 1.1)
  ;;=> "1.1"
  
  (float1 -10.23)
  ;;=> "-10.2"
  
  (float1 -10)
  ;;=> "-10"
  
  (float1 -0)
  ;;=> "0"
  
  (float1plus 0.0)
  ;;=> "0"
  
  (float1plus 1)
  ;;=> "+1"
  
  (float1plus 10.12)
  ;;=> "+10.1"
  
  (float1plus 1199990.0)
  ;;=> "+1199990"
    
  ())


(defn fresh? [ts]
  (when ts
    (jt/after? (jt/instant ts) (jt/minus (jt/instant) (jt/minutes 80)))
    ))

(comment
  
  (fresh? "2025-01-26T21:00:40.793883+08:00")
  ;;=> false
  
  ,)


(defn format-t [t t-delta]
  (when t
    (let [arr (when t-delta 
                (cond
                  (> t-delta 0.8) " ↑"
                  (< t-delta -0.8) " ↓"
                  :else nil))]
      (str "<b>" (float1plus t) arr "</b> <i>\u00b0C</i>"))
    ,))


(defn format-p [p]
  (when p
    (str "<b>" (int (hpa-mmhg p)) "</b> <i>мм.рст</i>")))


(defn format-wind [w g b]
  (when w
    (str "<b>" (int w) (when (and g (not= w g)) (str "-" (int g))) "</b> <i>м/с</i>"
         (when-let [r (wind-rhumb b)] (str " (<b>" r "</b>)")))
    ,))


(defn kbd-fav-subs [st is-fav]
  {:inline_keyboard
   [[
     (if is-fav
       {:text  "⭐️" :callback_data (str "fav:del:" st)}
       {:text  "➕" :callback_data (str "fav:add:" st)}
       )
     {:text "⏰" :callback_data (str "subs:add" st ":_" (next-seq))}
     ,]]})


;; https://en.wikipedia.org/wiki/List_of_emojis
;; https://unicode.org/emoji/charts/full-emoji-list.html

(defn st-info [{:keys [st title descr elev last distance last_ts is-fav]} show-info-link]
  (let [{:keys [t t_ts t_delta
                p p_ts
                w g b w_ts g_ts]} last
        v_t (when (fresh? t_ts) (format-t t t_delta))
        v_p (when (fresh? p_ts) (format-p p))
        v_w (when (fresh? w_ts) 
              (format-wind w (when (fresh? g_ts) g) b))
        ]
    {:text (str
            "🔹 <b>" (hesc title) "</b>"  
            (when last_ts (str "  <i>'" (last-dt (ts->dt last_ts)) "</i>")) 
            (when is-fav " ⭐")
            "\n"
             (hesc descr) "\n"
            "\n"
            "<a href=\"" (meteo-st-link st) "\">"
            (when-not (or v_t v_p v_w) "⚠️ нет актуальных данных\n")
            "  " (->> [v_t v_p v_w] (remove nil?) (str/join ", "))
            ;; (when v_t (str "   " v_t "\n"))
            ;; (when v_p (str "   " v_p "\n"))
            ;; (when v_w (str "   " v_w "\n"))
            "</a>"
            "\n"
            "\n"
            "📌 " "/map_" st  (when elev (str "  ^" (int elev) " м"))
            (when distance (str ",  (" (int (/ distance 1000)) " км)"))
            "\n"
            (when show-info-link (str "ℹ️ /info_" st)) "\n"
            )
     :parse_mode "HTML"
     :link_preview_options {:is_disabled true}
     :reply_markup main-buttons
     })
  
  )

;; station-info
  ;;=> {:closed_at nil,
  ;;    :publ true,
  ;;    :last_ts "2025-01-25T19:45:40.793883+08:00",
  ;;    :elev 560.0,
  ;;    :title "Николов Посад",
  ;;    :note nil,
  ;;    :st "npsd",
  ;;    :lon 104.2486,
  ;;    :lat 52.228,
  ;;    :descr "пгт. Маркова, мрн Николов Посад",
  ;;    :last
  ;;    {:p_ts "2025-01-25T19:45:40.793883+08:00",
  ;;     :t_delta -0.15833333333332789,
  ;;     :t_ts "2025-01-25T19:45:40.793883+08:00",
  ;;     :t -20.0,
  ;;     :p 982.9726492309999,
  ;;     :p_delta -0.21887092358349491},
  ;;    :created_at "2013-02-17T15:40:04.648+09:00"}


(defn active-list-item [{:keys [st title descr elev distance last_ts is-fav]}]
  (str
   "🔹 <b>" (hesc title) "</b>"
   (when last_ts (str "  <i>'" (last-dt (ts->dt last_ts)) "</i>")) 
   (when is-fav " ⭐")
   "\n"
   (hesc descr) "\n"
   (when elev (str "^" (int elev) " м"))
   (when distance (str ",  (" (int (/ distance 1000)) " км)"))      
   "\n"
   ; "📌 " "/map_" st "\n"   
   "ℹ️ /info_" st
   "\n"
   ,))


;; 🔖📅⭐️  📝 ⏰
;; ℹ️
;; ★ ☆ ⭐
;; ❌ ✅ ❎ ☑️ ✔️ ✖️ ➕ ➖