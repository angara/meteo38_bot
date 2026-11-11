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
   [meteobot.metrics.reg :refer [inc-metric]]
   ,))


(set! *warn-on-reflection* true)


(defn process [cfg inst]
  (let [dt   (-> inst (jt/local-date-time (:timezone config)))
        hhmm (-> dt (jt/local-time dt) (jt/truncate-to :minutes))
        wdc  (-> dt (jt/day-of-week) (.getValue) (str))]
    (when-let [subs (seq (store/subs-hhmm hhmm wdc))]
      (log! ["process subs:" hhmm wdc (count subs)])
      ;; one digest message per user instead of a message per station
      (doseq [[user-id user-subs] (group-by :user_id subs)]
        (try
          (let [st-datas (map (fn [sb]
                                (inc-metric :meteobot/send-subs {:station (:st sb)})
                                (store/station-info (:st sb)))
                              user-subs)
                missing (count (filter nil? st-datas))]
            (when (pos? missing)
              (log! :warn ["process.sub missing st-info:" user-id missing]))
            (doseq [msg (fmt/st-digest (remove nil? st-datas))]
              (botapi/send-message cfg user-id msg))
            (log! ["process.sub sent:" user-id (- (count user-subs) missing)]))
          (catch Exception ex
            (log! :warn ["process.sub:" user-id ex]))))
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
  :start (let [args (mount/args)
               cfg {:apikey (:telegram-apikey args)
                    :telegram-http-proxy (:telegram-http-proxy args)}
               ]
           (sender-seq cfg process))
  :stop (when sender-proc
          (.close ^java.lang.AutoCloseable sender-proc)))
