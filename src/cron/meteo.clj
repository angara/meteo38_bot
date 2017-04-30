
(ns cron.meteo
  (:require
    [clj-time.core :as t]
    [clj-time.periodic :as tp]
    [chime :refer [chime-at]]
    [mount.core :refer [defstate]]
    [monger.collection :as mc]
    ;
    [mlib.conf :refer [conf]]
    [mlib.log :refer [warn]]
    ;
    [bots.db  :refer [db_meteo]]
    [meteo.db :refer [DAT_COLL HOURS_COLL]]))
;


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

(defn create-indexes [db]
  (try
    (mc/create-index db HOURS_COLL
      (array-map :hour 1 :st 1) {:unique true})
    (catch Exception e
      (warn "create-indexes:" e))))
;

(defstate indexes
  :start
    (create-indexes (db_meteo)))
;


(defn interval-fetch [t0 t1]
  (try
    (mc/find-maps (db_meteo) DAT_COLL
      {:ts {:$gte t0 :$lt t1}})
    (catch Exception e
      (warn "interval-fetch:" e))))
;

(defn prev-hour [time]
  (let [t1 (t/floor time t/hour)
        t0 (t/minus t1 (t/hours 1))]
    [t0 t1]))
;


(defn init-mmac [val]
  {:min val :max val :avs val :cnt 1})
;

(defn update-mmac [mmac val]
  {
    :min (min (:min mmac) val)
    :max (max (:max mmac) val)
    :avs (+   (:avs mmac) val)
    :cnt (inc (:cnt mmac))})
;

(defn update-res! [res data-point]
  (doseq [k [:t :p :h]]
    (when-let [val (get data-point k)]
      (assoc! res k
        (if-let [mmac (get res k)]
          (update-mmac mmac val)
          (init-mmac val))))))
;

(defn aggregate [data]
  (doseq [[st_id st_data] (group-by :st data)]
    (prn "st_data:" st_id " - " (count st_data) "\n")
    (let [res (transient {})]
      (doseq [d st_data]
        (update-res! res d)))))
;


(defn af []
  (let [[t0 t1] (prev-hour (t/now))
        data (interval-fetch t0 t1)]
    (time
      (aggregate data))))
;

#_(af)

(defn worker [time]
  (prn "worker time:" time))
;

(defn start [cnf]
  (let [offset    (t/seconds (:offset cnf))
        interval  (t/seconds (:interval cnf))
        pseq      (tp/periodic-seq
                    (t/plus (t/floor (t/now) t/hour) offset)
                    interval)]
    (chime-at pseq worker)))
;

(defn stop [cancel]
  (when cancel
    (cancel)))
;



(defstate cron
  :start
    (if-let [cnf (-> conf :cron :meteo)]
      (start cnf)
      false)  ;; disabled in config
  :stop
    (stop cron))
;

;;.
