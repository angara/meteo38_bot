
(ns angara.util)


(defn chat-creds
  "returns apikey and chat id to use in reply"
  [msg]
  [(-> msg :apikey) (-> msg :chat :id)])
;

;;.
