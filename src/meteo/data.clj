(ns meteo.data
  (:import
    [com.mongodb DuplicateKeyException])
  (:require
    [taoensso.timbre :refer [debug warn]]
    [mount.core :refer [defstate]]
    [clj-time.core :as tc]
    [monger.collection :as mc]
    [monger.query :as mq]
    [mlib.core :refer [try-warn]]
    [meteo38-bot.db :refer [dbc]]
  ))


(def USER_TRACK_LL_NUM 1000)

(defonce sess-store (atom {}))
  ;; {sid {params}}

(defn sess-params [sid]
  (get @sess-store sid))
;

(defn sess-save [sid params]
  (swap! sess-store update-in [sid] #(merge % {:ts (tc/now)} params)))
;

(defn sess-cleanup [& [time-interval]]
  (let [age (tc/minus (tc/now) (or time-interval (tc/days 3)))]
    (doseq [[cid {ts :ts}] @sess-store]
      (when (and ts (tc/before? ts age))
        (swap! sess-store dissoc cid)))))
;


(def LOG-COLL "mbot_log")
;; {ts ll[] data}

(def USER-COLL "mbot_user")
;; {_id:tg-id, ts:<last>. start:{ts:ts, param:par}, locs: [{ts:ts ll:[]} ... ]}

(def FAVS-COLL "mbot_favs")
;; {_id: cid, ts:ts, favs:[...]}


(def SUBS-COLL "mbot_subs")
;; {_id, ts, cid:cid, ord:ord, del:bool
;;   time:"16:45", days:"01233456", ids:["uiii","npsd",...] }
;; idx: cid, idx: time


(defn start-user [uid data]
  (try
    (mc/insert (dbc) USER-COLL
      (merge {:_id uid :ts (tc/now)} data))
    (catch DuplicateKeyException e
      (debug "start-user duplicate:" uid (-> data :start :group)))
    (catch Exception e
      (warn "start-user:" e))))
;

(defn user-track [uid ll]
  (try
    (mc/update (dbc) USER-COLL {:_id uid}
      {:$push {:locs {:$each [{:ts (tc/now) :ll ll}]
                      :$slice USER_TRACK_LL_NUM}}})
    (catch Exception e
      (warn "user-track:" e))))
;

(defn mbot-log [msg]
  (let [{lat :latitude lng :longitude} (-> msg :message :location)
        ll (when (and lat lng) {:ll [lng lat]})]
    (try
      (mc/insert (dbc) LOG-COLL (merge {:ts (tc/now) :data msg} ll))
      (catch Exception e
        (warn "mbot-log:" e)))))
;

(defn ensure-indexes []
  (mc/ensure-index (dbc) LOG-COLL  (array-map :ts 1))
  (mc/ensure-index (dbc) LOG-COLL  (array-map :ll "2dsphere"))
  ;
  ;; user-coll
  ;
  (mc/ensure-index (dbc) SUBS-COLL (array-map :cid  1))
  (mc/ensure-index (dbc) SUBS-COLL (array-map :time 1)))
;

;; ;; ;; ;; ;;

(defn get-favs [cid]
  (try-warn "get-favs:"
    (:favs (mc/find-map-by-id (dbc) FAVS-COLL cid))))
;

(defn favs-add! [cid st-id]
  (let [favs (remove #{st-id} (get-favs cid))]
    (try-warn "favs-add:"
      (mc/update-by-id (dbc) FAVS-COLL cid
        {:$set {:ts (tc/now) :favs (conj (vec favs) st-id)}}
        {:upsert true}))))
;

(defn favs-del! [cid st-id]
  (try
    (mc/update-by-id (dbc) FAVS-COLL cid
      {:$pull {:favs st-id} :$set {:ts (tc/now)}})
    (catch Exception e (warn "favs-del:" e))))
;

;; ;; ;; ;; ;;

(defn get-subs [cid & [ord]]
  (try-warn "get-subs"
    #_{:clj-kondo/ignore [:invalid-arity]}
    (mq/with-collection (dbc) SUBS-COLL
      (mq/find (if ord {:cid cid :ord ord} {:cid cid}))
      (mq/sort (array-map :ord 1)))))
;

(defn subs-add! [cid ord time days ids]
  (try-warn "subs-add"
    (mc/insert (dbc) SUBS-COLL
      {:ts (tc/now) :cid cid :ord ord :time time :days days :ids ids})))
;

(defn subs-update! [cid ord params]
  (try-warn "subs-update"
    (mc/update (dbc) SUBS-COLL
      {:cid cid :ord ord}
      {:$set (merge {:ts (tc/now)} params)})))
;

(defn subs-remove! [cid ord]
  (try-warn "subs-remove"
    (mc/remove (dbc) SUBS-COLL {:cid cid :ord ord})))
;

(defn subs-hhmm [hhmm]
  (try-warn "subs-hhmm"
    #_{:clj-kondo/ignore [:invalid-arity]}
    (mq/with-collection (dbc) SUBS-COLL
      (mq/find {:time hhmm :del {:$ne true}}))))
;

(defstate data
  :start
    (ensure-indexes))
;
