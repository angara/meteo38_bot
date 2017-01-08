
(ns bots.main
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [taoensso.timbre :refer [error info]]
    [mount.core :refer [start-with-args]]
    [skichat.poller]))
;

(defn -main [& args]
  (if-let [rc (-> args first slurp edn/read-string)]
    (start-with-args rc)
    (error "config profile must be in parameters!")))
  ;
;

;;.
