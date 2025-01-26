(ns meteobot.app.inbound
  (:require 
   [taoensso.telemere :refer [log!]]
   [meteobot.app.command :refer [route-command route-text route-location]]
   ,))



(defn message [cfg {:keys [chat text location] :as msg}]

  (log! ["inbound/message.text:" text])
     
  (or
   (route-command cfg msg text)
   (route-text cfg msg text)
   (route-location cfg msg location)
   (do
     (log! ["the message was not handled:" msg])
     true)
   ,))


(defn unhandled [_ctx raw-data]
  (log! ["unhandled:" raw-data])
  nil)
