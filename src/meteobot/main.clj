(ns meteobot.main
  (:gen-class)
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :as mount]
   [meteobot.config :refer [build-info make-config]]
   [meteobot.data.pg]
   [meteobot.app.serv]
   [meteobot.app.sender]
   [meteobot.metrics.export]
   ,))


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
