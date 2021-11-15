(ns ^:skip-wiki com.grzm.aws.http.grzm
  (:require [com.grzm.aws.http :as aws]
            [com.grzm.aws.http-client :as impl]))

(set! *warn-on-reflection* true)

(defn create
  []
  (let [c (impl/create {:trust-all true})]  ;; FIX :trust-all
    (reify aws/HttpClient
      (-submit [_ request channel]
        (impl/submit c request channel))
      (-stop [_]
        (impl/stop c)))))
