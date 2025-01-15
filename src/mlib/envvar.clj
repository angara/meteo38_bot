(ns mlib.envvar)


(defn env-value [env default type-cast-fn]
  (if-let [value (System/getenv env)]
    (try
      (type-cast-fn value)
      (catch Exception ex
        (throw (ex-info "env-value type cast error" {:env env :value value} ex))))
    default))


(defn env-str
  ([env] (env-str env nil))
  ([env default]
   (env-value env default identity)))


(defn env-int
  ([env] (env-int env nil))
  ([env default]
   (env-value env default parse-long)))


(defn env-float
  ([env] (env-float env nil))
  ([env default]
   (env-value env default parse-double)))


(defn env-boolean
  ([env] (env-boolean env nil))
  ([env default]
   (env-value env default #(or (some-> (parse-long %) (not= 0))
                               (parse-boolean %)))))
