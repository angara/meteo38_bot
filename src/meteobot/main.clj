(ns meteobot.main
  (:gen-class)
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :as mount]
   [meteo.data]
   [meteo.poll]
   [meteo.sender]
    ;
   [cron.meteo]
   [meteobot.config :refer [build-info make-config]]
   [meteobot.app.serv]
   ))


(defn -main []
  (log! ["init:" (build-info)])
  (try
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(mount/stop)))
    (->
     (make-config)
     (mount/start-with-args)
     (as-> $ (log! ["started:" (str (:started $))])))
    (catch Exception ex
      (log! {:level :warn :error ex :msg "exception in main"})
      (Thread/sleep 2000)
      (System/exit 1))))
