
(ns meteo.stform
  (:import [java.util Locale])
  (:require
    [clojure.string :as s]
    [clj-time.core :as tc]
    [mlib.time :refer [hhmm ddmmyy]]
    [meteo.util :refer [md-link gmaps-link meteo-st-link]]))
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

(defn nf [x]
  (let [n (String/format Locale/ROOT "%.1f" (to-array [(float x)]))]
    (if-let [m (re-matches #"^(.+)(\.0*)$" n)]
      (m 1)
      n)))
;

(defn t-plus [t]
  (if (< 0 t)
    (str "+" (nf t))
    (nf t)))
;

(defn format-t [t]
  (when t
    (str "Температура: *" (t-plus t) "* \u00b0C\n")))

(defn format-h [h]
  (when h
    (str "Влажность: *" (nf h) "* %\n")))

(defn format-p [p]
  (when p
    (str "Давление: *" (nf (hpa-mmhg p)) "* мм.рт\n")))

(defn format-wind [w g b]
  (when w
    (str
      "Ветер: *" (nf w) (when g (str "-" (nf g))) "* м/с"
      (when-let [r (wind-rhumb b)] (str " (*" r "*)"))
      "\n")))
;

(defn format-water [t l]
  (when (or t l)
    (str "Вода:"
      (when t (str " *" (t-plus t) "* \u00b0C"))
      (when l (str " *" (nf l) "* м"))
      "\n")))
;

(defn format-st [st]
  (when (and st (:pub st))
    (let [data  (:last st)
          dist  (:dist st)
          ts    (:ts data)
          ;;gl    (gmaps-link (:ll st))
          gl    (meteo-st-link (:_id st))
          fresh (tc/minus (tc/now) (tc/minutes 70))]
      (str
        "*" (:title st) "*\n"
        (if (and ts (tc/after? ts fresh))
          (str
            (format-t (:t data))
            (format-h (:h data))
            (format-p (:p data))
            (format-wind (:w data) (:g data) (:b data))
            (format-water (:wt data) (:wl data)))
          (str "Нет данных.\n"))
        ;
        (when-let [d (:descr st)]
          (str (md-link d gl) "\n"))
        (when-let [a (:addr st)]
          (str (md-link a gl) "\n"))
        "'" (tmf ts)
        (when dist (str " \u00A0(" (nf (/ dist 1000)) " км)"))
        "\n"))))
;

;;.
