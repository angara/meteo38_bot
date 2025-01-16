(ns meteobot.config
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [malli.core :as m]
   [malli.error :as me]
   [mount.core :refer [defstate args]]
   [mlib.envvar :refer [env-str env-int]]
   ,))


(defn build-info []
  (-> "build-info.edn" (io/resource) (slurp) (edn/read-string)))


(defn env-config []
  {:telegram-apikey   (env-str "TELEGRAM_APIKEY")
   :database-url      (env-str "DATABASE_URL")
   :meteo-api-url     (env-str "METEO_API_URL", "https://angara.net/meteo/api")
   :meteo-api-auth    (env-str "METEO_API_AUTH")
   :meteo-api-timeout (env-int "METEO_API_TIMEOUT" 5000) ;; ms

   ;
;;    :meteo-http-host    (env-str "METEO_HTTP_HOST" "localhost")
;;    :meteo-http-port    (env-int "METEO_HTTP_PORT" 8004)
   ;
   ; :redis-url           (env-str "REDIS_URL")                 ;; "redis://user:password@localhost:6379/"
   ;
   :build-info (build-info)})


(def not-blank
  (m/schema [:re #"[^\s]+"]))

(def pos-int
  (m/schema [:and :int [:> 0]]))


(def config-schema
  [:map 
   [:telegram-apikey not-blank]
   [:database-url    not-blank]
   [:meteo-api-url   not-blank]
   [:meteo-api-auth  not-blank]
   [:meteo-api-timeout [:and :int [:> 100]]]
   ])


(defn validate-config [cfg]
  (if (m/validate config-schema cfg)
    cfg
    (->> cfg
         (m/explain config-schema)
         (me/humanize)
         (ex-info "invalid-config")
         (throw))))


(defn make-config []
  (validate-config (env-config)))


(defstate config 
  :start (args))

