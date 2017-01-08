
(ns skichat.weather.fmt
  (:import
    [java.util Locale])
  (:require
    [clojure.string :as s]
    [clj-time.core :as tc]
    [mlib.time :refer [hhmm ddmmyy]]))
;

(defn hpa-mmhg [h]
  (when h (/ h 1.3332239)))
;

(defn wind-rhumb [b]
  (when (and b (<= 0 b) (< b 360))
    (let [rh (int (mod (Math/floor (/ (+ b 22) 45)) 8))]
      (["С" "СВ" "В" "ЮВ" "Ю" "ЮЗ" "З" "СЗ"] rh))))
;

(defn tmf [ts]
  (if (tc/before? ts (tc/minus (tc/now) (tc/hours 12)))
    (str (ddmmyy ts) " " (hhmm ts))
    (hhmm ts)))
;

(def rus-mmm
  ["янв" "фев" "мар" "апр" "май" "июн" "июл" "авг" "сен" "окт" "ноя" "дек"])
;

(defn dd-mmm [ts]
  (when ts
    (str (tc/day ts) " " (get rus-mmm (dec (tc/month ts)) "???"))))
;

(defn nf [x]
  (let [n (String/format
            Locale/ROOT "%.1f" (to-array [(float x)]))]
    (if-let [m (re-matches #"^(.+)(\.0*)$" n)]
      (m 1)
      n)))
;

(defn t-plus [t]
  (if (< 0 t)
    (str "+" (nf t))
    (nf t)))
;

;;; ;;; ;;; ;;; ;;;

(defn format-t [t & [tmax]]
  (when t
    (str
      "Температура: *" (t-plus t)
      (when tmax (str " ... " (t-plus tmax)))
      "* \u00b0C")))
;

(defn format-p [p]
  (when p
    (str "Давление: *" (nf (hpa-mmhg p)) "* мм.рт")))
;

(defn format-h [h]
  (when h
    (str "Влажность: *" (nf h) "* %")))
;

(defn format-wind [w g b]
  (when w
    (str
      "Ветер: *" (nf w) (when g (str "-" (nf g))) "* м/с"
      (when-let [r (wind-rhumb b)] (str " (*" r "*)")))))

;

(defn format-water [t l]
  (when (or t l)
    (str "Вода:"
      (when t (str " *" (t-plus t) "* \u00b0C"))
      (when l (str " *" (nf l) "* м")))))

;

;;.
