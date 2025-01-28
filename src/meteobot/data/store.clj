(ns meteobot.data.store
  (:require
   [clojure.core.cache.wrapped :as cache]
   [clojure.core.memoize :as memo]
   [meteobot.data.meteo-api :as mapi]
   [meteobot.config :refer [config]]
   ))


(def ^:const ST_ACTIVE_TTL 5000) ;; XXX:!!!
(def ^:const ST_INFO_TTL   5000) ;; XXX:!!!


(def active-stations-cached-fn
  (memo/ttl mapi/active-stations :ttl/threshold ST_ACTIVE_TTL))

(defn active-stations [lat lon hours]
  (active-stations-cached-fn {:lat lat :lon lon :hours hours} config))



(def station-info-cached-fn
  (memo/ttl mapi/station-info :ttl/threshold ST_INFO_TTL))

(defn station-info [st]
  (station-info-cached-fn st config))


(comment 
  (station-info "npsd")
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
  ())


;; XXX:!!! 5 min ?
(def user-location* (cache/ttl-cache-factory {} :ttl 10000))


(defn set-user-location [user-id location]
  (swap! user-location* assoc user-id location))


(defn get-user-location [user-id]
  (cache/lookup user-location* user-id))


(comment
  
  user-location*
  
  (set-user-location 1 :a)
  (set-user-location 2 :b)
  
  (get-user-location 1)
  (get-user-location 2)

  ,)