(ns meteo.util
  (:require
   [clj-time.core :as tc]
   [mlib.conf :refer [conf]]
   ))


(def FRESH_INTERVAL (tc/minutes 80))


(defn apikey []
  (-> conf :telegram-apikey))


(def wd-map
  {\0 "вс" \1 "пн" \2 "вт" \3 "ср" \4 "чт" \5 "пт" \6 "сб"})


(defn md-link [text url]
  (str "[" text "](" url ")"))


(defn meteo-st-link [st-id]
  (str "https://angara.net/meteo/st/" st-id))


;; (defn gmaps-link [ll & [{z :z t :t}]]
;;   (let [c (str (second ll) "," (first ll))]
;;     (str "https://maps.google.com/maps?"
;;           "&q=loc:" c "&ll=" c "&t=" (or t "h") "&z=" (or z "18"))))
  ;; t = m,k,h,p
  ;; https://moz.com/blog/new-google-maps-url-parameters


(defonce inline-kbd-serial (atom 0))

;; telegram message update workaround
(defn inkb [] (swap! inline-kbd-serial inc))


(def st-alive-days 7)

(defn q-st-alive []
  { :pub 1
    :ts {:$gte (tc/minus (tc/now) (tc/days st-alive-days))}})
;

(defn default-locat []
  {:latitude 52.27 :longitude 104.27})


(defn default-favs []
  ["irgp" "uiii" "npsd" "olha"])


(defn cid [msg]
  (-> msg :chat :id))
;

(defn locat-ll [locat]
  [(:longitude locat) (:latitude locat)])
;

(defn fresh-last [data]
  (let [res (:last data)]
    (when-let [ts (:ts res)]
      (when (tc/after? ts (tc/minus (tc/now) FRESH_INTERVAL))
        res))))
;  


(def BTN_FAVS_TEXT "⭐️ Избранное")
(def BTN_NEAR_NEXT "🌍 Рядом")

(def main-buttons
  {:resize_keyboard true
   :keyboard [[{:text "Избранное"}
               {:text "Рядом" :request_location true}]]})

;; (def main-buttons
;;   { :resize_keyboard true
;;     :keyboard
;;       [[{:text "Погода"}
;;         {:text "Рядом" :request_location true}
;;         {:text "Меню"}]]})
;

;;.

;; ⭐️🌤🌍🔅🔆

; (def ic-havy-check        "\u2714")   ;; ✔
; (def ic-white-havy-check  "\u2705")   ;; ✅
; (def ic-havy-plus         "\u2795")   ;; ➕
; (def ic-havy-minus        "\u2796")   ;; ➖
; (def ic-glowing-star      "\uD83C\uDF1F")   ;; 🌟
