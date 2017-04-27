
(ns cron.meteo
  (:require
    [clj-time.core :as t]
    [clj-time.periodic :as tp]
    [chime :refer [chime-at]]
    [mount.core :refer [defstate]]
    ;
    [mlib.conf :refer [conf]]
    [mlib.log :refer [warn]]
    ;
    [bots.db  :refer [mdb_meteo]]
    [meteo.db :refer [HOURS_COLL]]))
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
