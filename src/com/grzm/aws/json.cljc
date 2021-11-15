(ns com.grzm.aws.json
  (:require #?(:bb [cheshire.core :as json]
               :clj [clojure.data.json :as json])))

(defn write-str
  [data]
  #?(:bb (json/generate-string data)
     :clj (json/write-str data)))

(defn read-str
  [readerable]
  #?(:bb (json/parse-string (slurp readerable) true)
     :clj (json/read-str (slurp readerable) :key-fn keyword)))
