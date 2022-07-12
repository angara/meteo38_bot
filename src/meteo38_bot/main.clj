(ns meteo38-bot.main
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [mount.core :refer [start with-args]]
    [taoensso.timbre :refer [info warn] :as timbre]
    [taoensso.timbre.tools.logging :refer [use-timbre]]
    ;
    [meteo.data]
    [meteo.poll]
    [meteo.sender]
    ;
    [cron.meteo]
    [forumnews.core]
    [mlib.conf :refer [build-info]]
  ))


(defn setup-logger! [app-log-patterns log-level]
  (use-timbre)
  (timbre/merge-config!
    {:output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
     :min-level [[app-log-patterns (keyword log-level)]
                 [#{"*"} :info]]}
  ))


(defn -main [& _]
  (setup-logger! #{"meteo38-bot.*"} :debug)
  (info "start:" @build-info)

  (if-let [rc (-> (System/getenv "CONFIG_EDN") slurp edn/read-string)]
    (start (with-args rc))
    (warn "config profile must be in parameters!")
  ))

