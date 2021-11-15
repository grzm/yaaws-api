;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ^:skip-wiki com.grzm.aws.protocols.json
  "Impl, don't call directly."
  (:require [com.grzm.aws.client :as client]
            [com.grzm.aws.protocols.common :as common]
            [com.grzm.aws.service :as service]
            [com.grzm.aws.shape :as shape]
            [com.grzm.aws.util :as util]))

(set! *warn-on-reflection* true)

(defmulti serialize
  (fn [_ shape _data] (:type shape)))

(defmethod serialize :default
  [_ shape data]
  (shape/json-serialize shape data))

(defmethod serialize "structure"
  [_ shape data]
  (->> (util/with-defaults shape data)
       (shape/json-serialize shape)))

(defmethod client/build-http-request "json"
  [service {:keys [op request]}]
  (let [{:keys [_jsonVersion _targetPrefix]} (:metadata service)
        operation                          (get-in service [:operations op])
        input-shape                        (service/shape service (:input operation))]
    {:request-method :post
     :scheme         :https
     :server-port    443
     :uri            "/"
     :headers        (common/headers service operation)
     :body           (serialize nil input-shape (or request {}))}))

(defmethod client/parse-http-response "json"
  [service {:keys [op] :as _op-map} {:keys [status _headers body] :as http-response}]
  (if (:cognitect.anomalies/category http-response)
    http-response
    (let [operation (get-in service [:operations op])
          output-shape (service/shape service (:output operation))
          body-str (util/bbuf->str body)]
      (if (< status 400)
        (if output-shape
          (shape/json-parse output-shape body-str)
          {})
        (common/json-parse-error http-response)))))
