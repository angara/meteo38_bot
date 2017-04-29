
(ns meteo.db
  (:require
    [mount.core :refer [defstate]]
    ;
    [monger.core :as mg]
    [monger.collection :as mc]
    [monger.query :as mq]
    [monger.conversion :refer [from-db-object]]
    ;
    [mlib.log :refer [warn try-warn]]
    [mlib.conf :refer [conf]]
    ;
    [bots.db :refer [db_meteo]]))
;


(def ST "st")   ;; stations collection

(def ST_COLL  "st")
(def DAT_COLL "dat")

(def HOURS_COLL "hours")   ;; houlry aggregates
(comment
  { :_id  :oid
    :hour "date rounded to hours"
    :st   "station_id"
    :t [:min :max :avg]
    :p [:min :max :avg]
    :h [:min :max :avg]
    :w [:min :max :avg]
    :b "int: 0-359, 360"
    :wt :avg
    :wl :avg})
;

; (defn q-st-fresh []
;   { :pub 1
;     :ts {:$gte (tc/minus (tc/now) (tc/minutes 80))}})

;;
;; db.st.ensureIndex({ll:"2dsphere"})
;; db.st.find({ll:
;;    {$near:{$geometry:{type:"Point",coordinates:[104.2486, 52.228]}}}
;; })
;
; https://docs.mongodb.com/manual/reference/command/geoNear/#dbcmd.geoNear
;
; db.runCommand({
;   geoNear: "st",
;   spherical: true,
;   near: {type:"Point", coordinates:[104.2486, 52.228]}
;   query: {pub:1}
;   limit: 100
; })

; https://docs.mongodb.com/manual/tutorial/calculate-distances-using-spherical-geometry-with-2d-geospatial-indexes/

; (def distance-multiplier-mi 3963.1906)
; (def distance-multiplier-km 6378.1370)


;; http://www.movable-type.co.uk/scripts/latlong.html


; db.location_data.aggregate(
;     {$geoNear:{}
;         near : {type : 'Point', coordinates : [127.0189206,37.5168266]},
;         distanceField : 'distance',
;         spherical : true,
;         maxDistance : 2000} )


(defn st-near
  "returns [{:dis M :obj {:_id ... :ll [long lat} ...} ...]"
  [ll query]
  (try-warn "st-near:"
    (let [res (from-db-object
                (mg/command (db_meteo)
                  (array-map
                    :geoNear ST
                    :near {:type "Point" :coordinates ll}
                    :spherical true
                    :query query
                    :limit 1000))
                true)]
      (if (< 0 (:ok res))
        (map #(assoc (:obj %) :dist (:dis %)) (:results res))
        (warn "st-near:" ll res)))))
;

(defn st-by-id [id & [fields]]
  (try-warn "st-by-id:"
    (mc/find-map-by-id (db_meteo) ST id (or fields []))))
;


(defn st-find
  "fetch list of public stations data"
  [q & [fields]]
  (try-warn "st-by-id:"
    (mq/with-collection (db_meteo) ST
      (mq/find q)
      (mq/fields (or fields []))
      (mq/sort (array-map :title 1)))))
      ;(mq/limit 1000))
;


(defn st-ids
  "fetch list of public stations data"
  [ids & [fields]]
  (when ids
    (try-warn "st-by-id:"
      (mc/find-maps (db_meteo) ST {:_id {:$in ids} :pub 1} (or fields [])))))
;

;;.
