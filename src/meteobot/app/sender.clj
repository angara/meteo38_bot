(ns meteobot.app.sender
  (:require
   [java-time.api :as jt]
   [mount.core :as mount]
   [chime.core :as chime]
   [taoensso.telemere :refer [log!]]
   [mlib.telegram.botapi :as botapi]
   [meteobot.config :refer [config]]
   [meteobot.app.fmt :as fmt]
   [meteobot.data.store :as store]
   ,))


(set! *warn-on-reflection* true)


(defn process [cfg inst]
  (let [dt   (-> inst (jt/local-date-time (:timezone config)))
        hhmm (-> dt (jt/local-time dt) (jt/truncate-to :minutes))
        wdc  (-> dt (jt/day-of-week) (.getValue) (str))]
    (when-let [subs (seq (store/subs-hhmm hhmm wdc))]
      (log! ["process subs:" hhmm wdc (count subs)])
      (doseq [{:keys [user_id st] :as sb} subs]
        (try
          (if-let [st-data (store/station-info st)]
            (do (botapi/send-message cfg user_id (fmt/st-brief st-data))
                (log! ["process.sub sent:" st user_id]))
            (log! :warn ["process.sub missing st-info:" st]))
          (catch Exception ex
            (log! :warn ["process.sub:" sb ex]))))
      ,)))


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
  :start (let [cfg {:apikey (:telegram-apikey (mount/args))}]
           (sender-seq cfg process))
  :stop (when sender-proc
          (.close ^java.lang.AutoCloseable sender-proc)))

