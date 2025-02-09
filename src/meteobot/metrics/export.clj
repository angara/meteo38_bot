(ns meteobot.metrics.export
  (:require
   [taoensso.telemere :refer [log!]]
   [mount.core :as mount]
   [meteobot.metrics.reg :as reg]
   [iapetos.export :refer [text-format]]
   [org.httpkit.server :refer [run-server]]
   ))

;; https://http-kit.github.io/http-kit/org.httpkit.server.html


(def HEADERS 
  {"Content-Type" "application/openmetrics-text; version=1.0.0; charset=utf-8"})


(defn metrics-response [_]
  {:status 200
   :headers HEADERS
   :body (text-format reg/registry)})


(mount/defstate metrics-endpoint
  :start (let [{:keys [metrics-bind metrics-port]} (mount/args)]
           (if (not= 0 metrics-port)
             (do 
               (reg/set-metric-hooks)
               (log! ["metrics-endpoint bind:" metrics-bind metrics-port])
               (run-server metrics-response {:ip metrics-bind :port metrics-port}))
             (do
               (log! ["metrics-endpoint disabled!"])
               false)))
  :stop (when metrics-endpoint 
          (metrics-endpoint)))


(comment

  (print
   (text-format reg/registry))

  ;; Content-Type: text/plain; version=0.0.4; charset=utf-8
  ;; Content-Type: application/openmetrics-text; version=1.0.0; charset=utf-8
  
  ,)
