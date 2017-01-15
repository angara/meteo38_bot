
(ns angara.db
  (:require
    [clojure.core.async :refer [<!! chan close!]]
    [taoensso.timbre :refer [debug info warn]]
    [clj-time.core :as tc]
    [mount.core :refer [defstate]]
    [monger.collection :as mc]
    [monger.query :as mq]

    [mlib.conf :refer [conf]]
    [mlib.mdb.conn :refer [dbc]]))
    ; [mlib.tlg.core :as tg]
    ; [mlib.tlg.poller :as tgp]))
;

(def LOC "abot_loc")
(comment
  "fields"
  [ :_id "oid"
    :ts  "now"
    :chat [:id :title]
    :user [:id :username :first_name :last_name]
    :ll   [:lng :lat]])
;

(def PHOTOS "abot_photos")
(comment
  "fields"
  [ :_id "sha(pict)"]
  [ :sizes
    [
      ["suffix" "width" "height"]
      ["suffix" "width" "height"]]])
;

(defn insert-loc [chat user {lat :latitude lng :longitude}]
  (if (and lat lng)
    (try
      (mc/insert (dbc) LOC
        {:ts (tc/now) :chat chat :user user :ll [lng lat]})
      (catch Exception e
        (warn "insert-loc:" e)))
    (warn "insert-loc: no location specified!")))
;

(defn last-loc [cid uid fresh-ts]
  (try
    (first
      (mq/with-collection (dbc) LOC
        (mq/find {"chat.id" cid "user.id" uid :ts {:$gte fresh-ts}})
        (mq/sort {"ts" -1})
        (mq/limit 1)))
    (catch Exception e
      (warn "last-loc:" cid uid e))))
;

(defn indexes []
  (try
    (mc/ensure-index (dbc) LOC (array-map :ts -1))
    (mc/ensure-index (dbc) LOC (array-map "user.id" 1))
    (mc/ensure-index (dbc) LOC (array-map "chat.id" 1))
    (mc/ensure-index (dbc) LOC (array-map "chat.id" 1 "user.id" 1 :ts -1))
    (mc/ensure-index (dbc) LOC (array-map :ll "2dsphere"))
    true
    (catch Exception e
      (warn "abot indexes:" e))))
;

(defstate abot
  :start
    (indexes))
;

;;.


; (defn start-user [uid data]
;   (try
;     (mc/insert (dbc) USER-COLL
;       (merge {:_id uid :ts (tc/now)} data))
;     (catch DuplicateKeyException e
;       (debug "start-user duplicate:" uid (-> data :start :group)))
;     (catch Exception e
;       (warn "start-user:" e))))
; ;
