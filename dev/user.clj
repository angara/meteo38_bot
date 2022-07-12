(ns user
  (:require
    [clojure.edn :as edn]
    [clojure.tools.namespace.repl :as tnr]
    [taoensso.timbre :refer [debug]]
    [mount.core :as mount]
    ;
    [meteo38-bot.main :refer [-main]]
  ))


(add-tap #(debug "tap>" %))

(def ARGS 
  (-> (System/getenv "CONFIG_EDN") (slurp) (edn/read-string)))


(defn restart []
  (mount/stop)
  (mount/start-with-args ARGS))

(defn reset []
  (tnr/refresh :after 'user/restart))


(comment

  (try
    (-main)
    (catch Exception ex
      ex
      )
    )

  (mount/start-with-args ARGS)

  (mount/start)
  (mount/stop)

  (restart)

  (reset)
  
  ,)
