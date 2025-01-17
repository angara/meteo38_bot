(ns meteobot.app.serv
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :refer [defstate args]]
   [mlib.telegram.botapi :refer [get-me seq-updates]]
   ,))


(set! *warn-on-reflection* true)


(defn update-handler [upd] 
  (log! ["update handler:" upd])
  ;; XXX: !!!
  )


(defn poller-loop [cfg handler]
  (log! ["start poller-loop"])
  (loop []
    (try
      (doseq [upd (seq-updates (-> cfg :telegram-apikey) nil)]
        (handler upd))
      (catch InterruptedException ex
        (log! ["poller-loop interrupted"])
        (throw ex))
      (catch Exception ex
        (log! {:level :warn :error ex :msg ["poller-loop exception"]})
        (Thread/sleep 3000)
        ))
    (recur)
    ))


(defstate bot-info
  :start (get-me (-> (args) (:telegram-apikey))))


(defstate poller
  :start (let [cfg (args)]
           (doto 
            (Thread. #(poller-loop cfg update-handler))
            (.start)
             ))
  :stop (do
          (log! ["stop poller"])
          (.interrupt ^Thread poller)))