(ns meteobot.data.store
  (:require
   [clojure.core.cache.wrapped :as cache]
   [clojure.core.memoize :as memo]
   [clojure.string :as str]
   [pg.core :refer [with-transaction]]
   [meteobot.data.meteo-api :as mapi]
   [meteobot.config :refer [config]]
   [meteobot.data.pg :as pg]
   [meteobot.data.sql :as sql]
   ))


;; 2 mins
(def ^:const ST_ACTIVE_TTL (* 120 1000))
(def ^:const ST_INFO_TTL   (* 120 1000))
(def ^:const USER_LOCATION_TTL (* 2 3600 1000)) ;; 2 hours

(def ^:const FAVS_MAX 10)


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


(def user-location* 
  (cache/ttl-cache-factory {} :ttl USER_LOCATION_TTL))


(defn set-user-location [user-id location]
  (swap! user-location* assoc user-id location))


(defn get-user-location [user-id]
  (cache/lookup user-location* user-id))


(defn- get-user-favs [conn user-id] 
  (-> conn
      (sql/user-favs {:user-id user-id})
      (first)
      (:favs)))


(defn user-favs [user-id]
  (get-user-favs pg/dbc user-id))


(defn user-fav-add [user-id st]
  (with-transaction [tx pg/dbc]
    (let [favs (get-user-favs tx user-id)
          favs (remove #(= % st) favs)
          ]
      (if (< (count favs) FAVS_MAX)
        (sql/save-favs tx {:user-id user-id 
                           :favs (-> (remove #(= % st) favs)
                                     (vec)
                                     (conj st))
                           })
        {:error (str "В избранном максимум " FAVS_MAX " элементов!")}
        ))
    ))


(defn user-fav-del [user-id st]
  (with-transaction [tx pg/dbc]
    (let [favs (get-user-favs tx user-id)]
      (sql/save-favs tx {:user-id user-id :favs (remove #(= % st) favs)})
      )))


; - - - - - - - - - -

(defn user-subs [user-id]
  (sql/user-subs pg/dbc {:user-id user-id}))


(defn user-subs-by-id [user-id subs-id]
  (first (sql/user-subs-by-id pg/dbc {:user-id user-id :subs-id subs-id})))


(defn user-subs-create [user-id subs]
  (first (sql/user-subs-create pg/dbc (assoc subs :user-id user-id))))


(defn user-subs-update [user-id subs-id hhmm wdays]
  (sql/user-subs-update pg/dbc {:user-id user-id :subs-id subs-id :hhmm hhmm :wdays wdays}))


(defn user-subs-delete [user-id subs-id]
  (sql/user-subs-delete pg/dbc {:user-id user-id :subs-id subs-id}))


(defn subs-hhmm [hhmm wday]
  (let [wd (str wday)]
    (->> 
     (sql/subs-hhmm pg/dbc {:hhmm hhmm})
     (filter #(str/includes? (:wdays %) wd))
     )))
