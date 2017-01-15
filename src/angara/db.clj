
(ns angara.db
  (:require
    [clojure.core.async :refer [<!! chan close!]]
    [taoensso.timbre :refer [debug info warn]]
    [clj-time.core :as tc]
    [mount.core :refer [defstate]]
    [monger.collection :as mc]

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
    :from [:id :username :first_name :last_name]
    :chat [:id :title]
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

(defn insert-loc [chat from {lat :latitude lng :longitude}]
  (if (and lat lng)
    (try
      (mc/insert (dbc) LOC {:ts (tc/now) :chat chat :from from :ll [lng lat]})
      (catch Exception e
        (warn "insert-loc:" e)))
    (warn "insert-loc: no location specified!")))
;

(defn indexes []
  (try
    (mc/ensure-index (dbc) LOC (array-map :ts -1))
    (mc/ensure-index (dbc) LOC (array-map "from.id" 1))
    (mc/ensure-index (dbc) LOC (array-map "chat.id" 1))
    (mc/ensure-index (dbc) LOC (array-map "chat.id" 1 "from.id" 1 :ts -1))
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
