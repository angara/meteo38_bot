(ns meteobot.app.inbound
  (:require 
   [taoensso.telemere :refer [log!]]
   [meteobot.app.command :refer [route-command route-text route-location route-callback]]
   ,))


(defn message [cfg {:keys [text location] :as msg}]
  (log! ["inbound/message.text:" text])
  (or
   (route-command cfg msg text)
   (route-text cfg msg text)
   (route-location cfg msg location)
   (do
     (log! ["message was not handled:" msg])
     true)
   ,))


(defn callback [cfg cbk]
  (log! ["inbound/callback:" cbk])
  (or
   (route-callback cfg cbk)
   (do
     (log! :warn ["callback was not handled:" cbk])
     true)
   ,))


(defn unhandled [_ctx raw-data]
  (log! ["unhandled:" raw-data])
  nil)
