(ns cron.meteo
  (:require
    [clj-time.core :as t]
    [clj-time.periodic :as tp]
    [chime :refer [chime-at]]
    [mount.core :refer [defstate]]
    [taoensso.timbre :refer [debug warn]]
    [monger.collection :as mc]
    [monger.query :as mq]
    ;
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int to-float]]
    ;
    [meteo38-bot.db  :refer [db_meteo]]
    [meteo.db :refer [DAT_COLL HOURS_COLL]]
  ))


(def ONE_HOUR (t/hours 1))


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
      (array-map :st 1) {})
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

(defn get-last-hour []
  (try
    (->
      #_{:clj-kondo/ignore [:invalid-arity]}
      (mq/with-collection (db_meteo) HOURS_COLL
        (mq/find {})
        (mq/fields [:hour])
        (mq/sort {:hour -1})
        (mq/limit 1))
      (first)
      (:hour))
    (catch Exception e
      (warn "get-last-hour:" e))))
;

(defn update-st-hour [st hour data]
  (debug "st-hour:" st hour data)
  (when data
    (try
      (->
        (mc/update (db_meteo) HOURS_COLL
          {:st st :hour hour}
          (assoc data :st st :hour hour)
          {:upsert true})
        (.getN)
        (= 1))
      (catch Exception e
        (warn "update-st-hour:" st hour data e)))))
;

(defn calc-avg [mmac]
  (let [{avs :avs cnt :cnt} mmac]
    (if cnt
      (assoc
        (dissoc mmac :avs :cnt)
        :avg (/ (float avs) cnt))
      ;;
      mmac)))
;

(defn min-max-avg [data]
  (when-let [v0 (first data)]
    (->> (next data)
      (reduce
        (fn [mmac val]
          (when val
            { :min (min (:min mmac) val)
              :max (max (:max mmac) val)
              :avs (+   (:avs mmac) val)
              :cnt (inc (:cnt mmac))}))
        {:min v0 :max v0 :avs v0 :cnt 1})
      (calc-avg))))
;

(defn calc-w [ws gs]
  (if-let [gm (:max gs)]
    (if-let [wm (:max ws)]
      (assoc ws :max (max wm gm))
      (assoc ws :max gm))
    ws))
;

(defn calc-b [bs]
  (when (seq bs)
    (let [low (apply min bs)
          hi  (apply max bs)]
      (when (< hi 360)
        (if (>= 90 (- hi low))
          (int
            (/ (+ hi low) 2))
          ;;
          (let [h2 (+ 360 low)]
            (when (>= 90 (- h2 hi))
              (int
                (rem (/ (+ h2 hi) 2) 360)))))))))
;

(defn numf [key]
  (fn [d]
    (when-let [v (get d key)]
      (or
        (and (number? v) v)
        (to-int v)
        (to-float v)))))
;

(defn st-aggregate [st data]
  (try
    (let [tph
            (for [k [:t :p :h :wt :wl]
                  :let [mma (min-max-avg (keep (numf k) data))]
                  :when mma]
              [k mma])
          w (calc-w
              (min-max-avg (keep (numf :w) data))
              (min-max-avg (keep (numf :g) data)))
          b (let [max_w (:max w)]
              (when (and max_w (< 0 max_w))
                (when-let
                  [b (calc-b (keep #(when-let [b (:b %)] b) data))]
                  {:avg b})))]
      (into {}
        (filter second
          (conj tph [:w w] [:b b]))))
    (catch Exception e
      (warn "st-aggregate:" st data e))))
;

;;; ;;; ;;; ;;; ;;;

(defn calc-hour [t0 t1]
  (if-let [data (not-empty (interval-fetch t0 t1))]
    (doseq [[st st_vals] (group-by :st data)]
      (update-st-hour st t0
        (st-aggregate st st_vals)))
    ;;
    (debug "calc-hour: no data - " t0 t1)))
;


(defn worker [this-hour]
  (if-let [last-hour (get-last-hour)]
    (loop [t0 (t/minus (t/floor last-hour t/hour) ONE_HOUR)]
      (debug "worker last-hour:" last-hour)
      (when (t/before? t0 this-hour)
        (let [t1 (t/plus t0 ONE_HOUR)]
          (calc-hour t0 t1)
          (recur t1))))
    ;;
    (warn "worker: unable to get last hour")))
;

(defn start [cnf]
  (let [offset    (t/seconds (:offset cnf))
        interval  (t/seconds (:interval cnf))
        pseq      (tp/periodic-seq
                    (t/plus (t/floor (t/now) t/hour) offset)
                    interval)]
    (debug "params:" offset interval)
    (chime-at pseq worker)))
;

(defn stop [cron]
  (when cron
    (cron)))    ;; stop it
;


(defstate cron
  :start
    (if-let [cnf (-> conf :cron :meteo)]
      (start cnf)
      (do
        (warn "cron.meteo did not start")
        false))
  :stop
    (stop cron))
;
