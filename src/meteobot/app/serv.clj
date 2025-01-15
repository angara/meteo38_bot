(ns meteobot.app.serv
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :refer [defstate]]
   [meteobot.telegram.bot :refer [bot-info]]
   ,))



(defn poller-loop [cfg]
  (log! ["poller-loop:" cfg])
  (loop []
    (try
      
      nil ;; XXX:!!!
      
      (catch InterruptedException ex
        (log! ["poller-loop interrupted"])
        (throw ex))
      (catch Exception ex
        (log! {:level :warn :error ex :msg ["poller-loop exception"]})
        (Thread/sleep 3000)
        ))
    (recur)
    ))


(defstate poller
  :start (let [cfg nil
               bot @bot-info
               ]
           (log! ["start poller"])
           (doto (Thread. #(poller-loop cfg)) (.start)))
  :stop (do
          (log! ["stop poller"])
          (.interrupt ^Thread poller)))