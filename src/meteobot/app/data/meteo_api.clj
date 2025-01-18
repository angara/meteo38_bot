(ns meteobot.app.data.meteo-api
  (:import [java.net URLEncoder])
  (:require
   [clojure.string :as str]
   [taoensso.telemere :refer [log!]]
   [org.httpkit.client :as http]
   [jsonista.core :as json]
   ))


(defn get-json [url {:keys [auth timeout]}]
  (let [hdrs (when auth {"Authorization" auth})
        {:keys [status body error]}
        #_{:clj-kondo/ignore [:unresolved-var]}
        @(http/get url {:headers hdrs :timeout timeout})]
    (if (= 200 status)
      (try
        (json/read-value body json/keyword-keys-object-mapper)
        (catch Exception ex
          (log! :warn ["get-json body parse:" (ex-message ex) url])
          nil))
      (do
        (if error
          (log! :warn ["get-json error:" (ex-message error) url])
          (log! :warn ["get-json:" status body url]))
        nil))))


(defn join-qs [params]
  (->> params
       (map
        (fn [[k v]]
          (when-not (nil? v)
            (str (URLEncoder/encode (name k), "UTF-8") "=" (URLEncoder/encode (str v), "UTF-8")))
          ))
       (str/join "&")
       ))

(comment
  
  (join-qs {:lat 55.1234 :lon 104 :hours 1})
  ;;=> "lat=55.1234&lon=104&hours=1"
      
  )


(defn active-stations [{:keys [meteo-api-url meteo-api-auth meteo-api-timeout lat lon hours]}]
  ;; lat=52.28&lon=104.28&last-hours=2
  (let [qs (join-qs {:lat lat :lon lon :hours hours})]
    (-> 
     (str meteo-api-url (str "/active-stations?" qs))
     (get-json {:auth meteo-api-auth :timeout meteo-api-timeout})
     (:stations)
     )))


(comment
  
  (require '[meteobot.config])

  (def cfg (meteobot.config/make-config))

  (take 3 (active-stations (assoc cfg :lat 52.2 :lon 104.28 :hours 2)))
  ;;=> ({:publ true,
  ;;     :last_ts "2025-01-18T23:12:27.251397+08:00",
  ;;     :elev 520.0,
  ;;     :title "Ботаника",
  ;;     :st "botanika7",
  ;;     :lon 104.277112,
  ;;     :lat 52.218503,
  ;;     :descr "Иркутский район, Ботаника, 7",
  ;;     :distance 2066.8288664879983,
  ;;     :created_at "2024-06-03T11:39:33.293+08:00"}
  ;;    {:publ true,
  ;;     :last_ts "2025-01-18T23:10:04.781172+08:00",
  ;;     :elev 520.0,
  ;;     :title "Ершовский",
  ;;     :st "ershi28",
  ;;     :lon 104.3187,
  ;;     :lat 52.2211,
  ;;     :descr "г. Иркутск, м-н Ершовский, 28",
  ;;     :distance 3529.554808087678,
  ;;     :created_at "2024-10-18T10:12:22.95802+08:00"}
  ;;    {:publ true,
  ;;     :last_ts "2025-01-18T23:12:25.1823+08:00",
  ;;     :elev 560.0,
  ;;     :title "Николов Посад",
  ;;     :st "npsd",
  ;;     :lon 104.2486,
  ;;     :lat 52.228,
  ;;     :descr "пгт. Маркова, мрн Николов Посад",
  ;;     :distance 3777.597010349891,
  ;;     :created_at "2013-02-17T15:40:04.648+09:00"})

  )
