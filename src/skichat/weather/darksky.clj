
(ns skichat.weather.darksky
  (:require
    [clojure.string :as s]
    [clj-time.core :as tc]
    [clj-time.coerce :refer [from-long]]
    [clj-http.client :as http]
    [taoensso.timbre :refer [warn]]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int]]
    [skichat.weather.fmt :as fmt]))
;

(defn get-forecast [latlng]
  (let [cnf (-> conf :weather :darksky)
        locat (str (first latlng) "," (second latlng))
        url (str (:api cnf) (:key cnf) "/" locat "/" (:params cnf))
        tout (:timeout cnf 3000)]
    (try
      (:body
        (http/get url
          {:as :json :socket-timeout tout :conn-timeout tout}))
      (catch Exception e (warn e)))))
;


(defn convert-data [data]
  (try
    { :time (-> data :time to-int (* 1000) from-long)
      :summary (-> data :summary)
      :temp-min (-> data :temperatureMin)
      :temp-max (-> data :temperatureMax)
      :pressure (-> data :pressure)
      :humidity
        (when-let [h (-> data :humidity)]
          (* 100 h))
      :clouds (-> data :cloudCover)
      :precip (-> data :precipIntensity)
      :precip-type (-> data :precipType) ;; "rain", "snow", "sleet"
      :wind (-> data :windSpeed)
      :wind-dir (-> data :windBearing)}
    (catch Exception e
      (warn "convert-data:" e data))))
;

(defn daily-filter [fcast day-num]
  (let [t0 (tc/minus (tc/now) (tc/hours 12))]
    (->>
      (get-in fcast [:daily :data])
      (map convert-data)
      (filter #(tc/before? t0 (:time %)))
      (take day-num))))
;


(defn format-precip [precip precip-type]
  ;; NOTE: precip-type
  (when (and precip (<= 0.001 precip))
    (str "Осадки: *" (fmt/nf (* 1000 precip)) "* мм")))
;

(defn daily-text [data]
  (let [t-min (:temp-min data)
        t-max (:temp-max data)]
    (remove nil?
      [
        (fmt/format-t t-min (when (not= t-min t-max) t-max))
        (fmt/format-p (:pressure data))
        (fmt/format-h (:humidity data))
        (fmt/format-wind (:wind data) nil (:wind-dir data))
        (format-precip (:precip data) (:precip-type data))])))
;

(defn forecast-text [place latlng day-num]
  (let [tz (tc/time-zone-for-id (:tz conf "Asia/Irkutsk"))
        fcast (->
                  (get-forecast latlng)
                  (daily-filter day-num)
                  not-empty)
        mk-time (fn [t] (fmt/dd-mmm (tc/to-time-zone t tz)))]
    ;
    (str "Прогноз погоды - *" place "*\n"
      (s/join "\n"
        (for [f fcast]
          (str
            "\n= " (mk-time (:time f)) " =\n"
            (s/join "\n" (daily-text f))))))))
;

; (forecast-text "Мамай" [51,104] 5)



;;.
