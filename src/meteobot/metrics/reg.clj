(ns meteobot.metrics.reg
  (:require
   [iapetos.core :as prom]
   [mlib.telegram.botapi :as botapi]
   [meteobot.data.meteo-api :as meteo-api]
   ,))


(def registry
  (->
   (prom/collector-registry)
   (prom/register
    
    (prom/counter :meteobot/internal-errors 
                  {:labels [:type :cause] :description "application internal error"})
    
    (prom/counter :meteobot/telegram-updates 
                  {:labels [:type] :description "telegram inbound updates"})
    
    (prom/counter :meteobot/telegram-api 
                  {:labels [:method :status] :description "telegram api-call status"})

    (prom/counter :meteobot/send-subs
              {:labels [:station] :description "send subscription data"})
    
    (prom/counter :meteobot/meteo-data-api
              {:labels [:method] :description "meteo-data api"})
    
    ,)))


(defn inc-metric [metric labels]
  (try
    (prom/inc registry metric labels 1)
    (catch Exception _
      (prom/inc registry 
                :meteobot/internal-errors 
                {:type "wrong_metric" :cause metric} 1))
    ,))


(defn set-metric-hooks []
  
  (alter-var-root #'botapi/*metric-hook*
                  (constantly (partial inc-metric :meteobot/telegram-api )))
  
  (alter-var-root #'meteo-api/*metric-hook*
                  (constantly (partial inc-metric :meteobot/meteo-data-api)))
  
  ,)

