(ns meteobot.app.data.meteo-api
  (:require
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


(defn active-stations [{:keys [meteo-api-url meteo-api-auth meteo-api-timeout]}]
  (-> 
   (str meteo-api-url "/active-stations?lat=52.28&lon=104.28&last-hours=2")
   (get-json {:auth meteo-api-auth :timeout meteo-api-timeout})
   (:stations)
   ))
