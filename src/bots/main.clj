
(ns bots.main
  (:require
    [clojure.edn :as edn]
    [mount.core :refer [start with-args]]
    [mlib.log :refer [warn]]
    [angara.process]
    [meteo.core]
    [cron.meteo]
    [skichat.process])
  (:gen-class))
;

(defn -main [& args]
  (if-let [rc (-> args first slurp edn/read-string)]
    (start (with-args rc))
    (warn "config profile must be in parameters!")))
  ;
;

;;.
