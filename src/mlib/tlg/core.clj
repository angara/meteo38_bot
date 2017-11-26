
(ns mlib.tlg.core
  (:require
    [mlib.log :refer [info warn]]
    [clj-http.client :as http]))
;


(def RETRY_COUNT    5)
(def SOCKET_TIMEOUT 10000)


(defn api-url [token method]
  (str "https://api.telegram.org/bot" token "/" (name method)))
;

(defn file-url [token path]
  (str "https://api.telegram.org/file/bot" token "/" path))
;


(defn api-try [method url data]
  (try
    (let [res (:body (http/post url data))]
      (if (:ok res)
        (:result res)
        (do
          (info "tg-api-try:" method res)
          (when
            (-> res (:error_code) (first) #{\3 \5}) ;; 3xx or 5xx codes
            ::recur))))
    (catch Exception e
      (do
        (warn "tg-api-try:" method (.getMessage e))
        ::recur))))
;

(defn api [token method params & [{timeout :timeout retry :retry}]]
  (let [tout (or timeout SOCKET_TIMEOUT)
        rmax (or retry RETRY_COUNT)
        url  (api-url token method)
        data { :content-type :json
               :as :json
               :form-params params
               :throw-exceptions false
               :socket-timeout tout
               :conn-timeout tout}]
    (loop [retry rmax]
      (if (< 0 retry)
        (let [rc (api-try method url data)]
          (if (= rc ::recur)  
            (recur (dec retry))
            rc))
        ;;
        (warn "tg-api:" "retry limit reached -" rmax)))))
;

(defn send-text [token chat text & [markdown?]]
  (api token :sendMessage
    (merge {:chat_id chat :text text}
      (when markdown? [:parse_mode "Markdown"]))))
;

(defn send-md [token chat text]
  (api token :sendMessage
    {:chat_id chat :text text :parse_mode "Markdown"}))
;

(defn send-html [token chat text]
  (api token :sendMessage
    {:chat_id chat :text text :parse_mode "HTML"}))
;

(defn send-message [token chat params]
  (api token :sendMessage (merge {:chat_id chat} params)))
;

(defn file-path [token file-id]
  ;; {:file_id "..." :file_size 999 :file_path "dir/file.ext"}
  (:file_path
    (api token :getFile {:file_id file-id})))
;

(defn get-file [token file-id & [{timeout :timeout}]]
  (if-let [path (file-path token file-id)]
    (try
      (:body
        (http/get (file-url token path)
          { :as :byte-array
            :socket-timeout (or timeout SOCKET_TIMEOUT)
            :conn-timeout   (or timeout SOCKET_TIMEOUT)}))
      (catch Exception e
        (warn "get-file:" file-id (.getMessage e))))
    ;
    (info "get-file - not path for file_id:" file-id)))
;

(defn send-file
  "params should be stringable (json/generate-string)
    or File/InputStream/byte-array"
  [token method mpart & [{timeout :timeout}]]
  (try
    (let [tout (or timeout SOCKET_TIMEOUT)
          res (:body
                (http/post (api-url token method)
                  { :multipart
                      (for [[k v] mpart]
                        {:name (name k) :content v :encoding "utf-8"})
                    :as :json
                    :throw-exceptions false
                    :socket-timeout tout
                    :conn-timeout tout}))]
          ;
      (if (:ok res)
        (:result res)
        (info "send-file:" method res)))
    (catch Exception e
      (warn "send-file:" method (.getMessage e)))))
;


(defn set-webhook-cert [token url cert-file]
  (http/post (api-url token :setWebhook)
    {:multipart [ {:name "url" :content url}
                  {:name "certificate" :content cert-file}]}))
;

;;.
