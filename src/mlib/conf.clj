(ns mlib.conf
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [mount.core :refer [defstate args]]
    [mlib.core :refer [deep-merge edn-resource]]
  ))


(def build-info
  (delay (-> "build-info" (io/resource) (slurp) (str/trim))))


(defstate conf
  :start
    (deep-merge
      (edn-resource "config.edn")
      {:build (edn-resource "build.edn")}
      (args)))
;
