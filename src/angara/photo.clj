
(ns angara.photo
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :refer [sh]]
    [clj-time.core :as tc]
    [monger.collection :as mc]
    ;
    [mlib.log :refer [debug info warn]]
    [mlib.conf :refer [conf]]
    [mlib.core :refer [to-int byte-array-hash hexbyte]]
    [mlib.tlg.core :refer [send-message send-text get-file]]
    ;
    [bots.db :refer [dbc]]
    [angara.db :refer [last-loc PHOTOS]]
    [angara.util :refer [chat-creds]]))
;

; (comment
;   (def apikey "174879986:AAHwiXbfHTRrVwczWPRNW1xtxeJjVSbnEeA")
;   (def file-id "AgADAgADkKgxG4ShBQAB-ELVmnHFvEKD5oENAARDxx-Oc6WV3wVdBAABAg")
;   (def file-path "photo/file_4.jpg")
;
;   (map #(format "%02x" %)
;     (byte-array-hash "sha1"
;       (get-file apikey file-id)))
;
;   (mlib.tlg.core/file-path apikey file-id))
; ;
;

(def FRESH_LOC (tc/minutes 30))


(defn insert-photo-rec [hash msg loc]
  (try
    (let [rec { :hash hash
                :ts (tc/now)
                :ll (:ll loc)
                :chat (:chat msg)
                :user (:from msg)
                :caption (str (:caption msg))}]
      (mc/insert-and-return (dbc) PHOTOS rec))
    (catch Exception e
      (warn "insert-photo-rec:" msg e))))
;

(defn base-path [hash]
  (.getPath
    (io/file (-> conf :bots :angarabot :photo-dir)
      (subs hash 0 2) (subs hash 2 4) hash)))
;

(defn save-orig [bytes fname]
  (prn "save:" fname)
  (io/make-parents fname)
  (io/copy bytes (io/file fname))
  fname)
;

(defn resize-xs [orig base]
  (let [convert (-> conf :photo :convert-bin)
        dest (str base "_xs.jpg")]
    (sh convert orig
      "-auto-orient" "-filter" "Lanczos" "-unsharp" "0x1"
      "-thumbnail" "90x90^"
      ;; "-gravity" "center" "-extent" "48x48"
      "-strip" "-quality" "75" dest)
    dest))
;

(defn resize-md [orig base]
  (let [convert (-> conf :photo :convert-bin)
        dest (str base "_md.jpg")]
    (sh convert orig
      "-auto-orient"
      "-filter" "Lanczos" "-unsharp" "0x1" "-thumbnail" "x240"
      ;; "-gravity" "center" "-extent" "320x240"
      "-strip" "-quality" "80" dest)
    dest))
;


(defn process-photo [bytes msg loc]
  (try
    (let [hash (apply str (map hexbyte (byte-array-hash "sha1" bytes)))
          photo-dir (-> conf :angarabot :photo-dir)
          bp (base-path hash)
          orig (save-orig bytes (str bp ".jpg"))]
      (resize-xs orig bp)
      (resize-md orig bp)
      (insert-photo-rec hash msg loc))
    (catch Exception e
      (warn "process-photo:" e))))
;

(defn big-photo-id [pho]
  (let [w+h (fn [p]
              (+ (-> p :with (to-int 0)) (-> p :height (to-int 0))))]
    (:file_id (first (sort-by w+h > pho)))))
;

(defn handle-photo [msg photo]
  (let [[apikey cid] (chat-creds msg)
        uid (-> msg :from :id)
        orig-id (big-photo-id photo)
        recent-loc-ts (tc/minus (tc/now) FRESH_LOC)]
    (if-let [loc (last-loc cid uid recent-loc-ts)]
      (if-let [bytes (get-file apikey orig-id)]
        (if-let [res (process-photo bytes msg loc)]
          (send-message apikey cid
            { :text (str "Фото привязано к координате - ["
                      (-> res :ll second) "," (-> res :ll first) "]")
              :reply_to_message_id (::message_id msg)})
          ;
          (warn "handle-photo - save failed"))
        ;
        (warn "handle-photo - unable to get photo:" msg))
      ;
      (debug "handle-photo:" "no recent location"))))
;

;;.
