
(ns meteo.sender
  (:require
    [clj-time.core :as tc]
    [mount.core :refer [defstate]]
    ;
    [mlib.conf :refer [conf]]
    [mlib.log :refer [debug warn]]
    [mlib.time :refer [hhmm]]
    [mlib.tlg.core :as tg]
    ;
    [meteo.db :refer [st-ids]]
    [meteo.data :refer [sess-cleanup subs-hhmm]]
    [meteo.stform :refer [format-st]]))
;


(def week-dayc {0 \0  1 \1  2 \2  3 \3  4 \4  5 \5  6 \6  7 \0})


(defn worker [apikey tm]
  (let [day-of-week
          (tc/day-of-week
            (tc/to-time-zone (tc/now) (tc/time-zone-for-id (:tz conf))))
        dc   (week-dayc day-of-week)
        subs (filter  #(some #{dc} (:days %))  (subs-hhmm tm))
        ids  (reduce  #(into %1 (:ids %2))  #{}  subs)
        stm  (into {} (for [s (st-ids ids)] [(:_id s) s]))]
    (doseq [s subs :let [cid (:cid s) ids (:ids s)]]
      (debug "time:" tm "subs:" (count subs) "ids:" (count ids))
      (doseq [id ids]
        (tg/send-message apikey cid
          { :text (format-st (stm id)) 
            :parse_mode "Markdown"
            :disable_web_page_preview true})))))
;

(defn minute-loop [run-flag cnf]
  (reset! run-flag true)
  (loop [prev ""]
    (let [tm (hhmm (tc/now))]
      (when (not= prev tm)
        (try
          (worker (:apikey cnf) tm)
          (sess-cleanup)
          (catch Exception e
            (warn "minute-loop:" e))))
      (when @run-flag
        (Thread/sleep (:sender-sleep cnf))
        (recur tm)))))
;

(defstate sender
  :start
    (let [run-flag (atom nil)
          cnf (-> conf :bots :meteo38bot)]
      {:run-flag run-flag
       :thread (-> #(minute-loop run-flag cnf) Thread. .start)})
  :stop
    (when sender
      (reset! (:run-flag sender) false)))


;;.
