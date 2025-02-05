(ns meteobot.app.sender
  (:require
   [clojure.string :as str]
   [java-time.api :as jt]
   [mount.core :as mount]
   [chime.core :as chime]
   [taoensso.telemere :refer [log!]]
   [meteobot.config :refer [config]]
   [meteobot.data.store :as store]
   ,))


(set! *warn-on-reflection* true)


;; (defn local-now []
;;   (jt/local-date-time (jt/instant) (:timezone config)))


(defn process [cfg inst]
  (let [dt (-> inst (jt/local-date-time (:timezone config)))
        hhmm (-> dt (jt/local-time dt) (jt/truncate-to :minutes))
        wdc (-> dt (jt/day-of-week) (.getValue) (str))]
 (log! ["process.time" hhmm wdc]) ;; XXX:!!!
      (when-let [subs (seq (store/subs-hhmm hhmm wdc))]
        (log! ["process subs:" hhmm wdc (count subs)])
        
        ;;
        
        
        )
      )
  )


(comment
  
  (process {} (jt/local-date-time 2025 2 5 6 0 0))
  
  ,)


(defn sender-seq [cfg handler]
  (log! ["sender-seq started"])
  (chime/chime-at 
   (chime/periodic-seq
    (jt/truncate-to (jt/instant) :minutes)
    (jt/duration 1 :minutes))
   #(handler cfg %)
   {:error-handler (fn [ex]
                     (log! :warn ["sender-loop" ex])
                     true
                     )}
   ,))


(mount/defstate sender-proc
  :start (sender-seq (mount/args) process)
  :stop (when sender-proc
          (.close ^java.lang.AutoCloseable sender-proc)))

