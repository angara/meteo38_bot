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
