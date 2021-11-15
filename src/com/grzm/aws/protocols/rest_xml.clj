;; Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns ^:skip-wiki com.grzm.aws.protocols.rest-xml
  "Impl, don't call directly."
  (:require [com.grzm.aws.client :as client]
            [com.grzm.aws.protocols.common :as common]
            [com.grzm.aws.protocols.rest :as rest]
            [com.grzm.aws.shape :as shape]))

(set! *warn-on-reflection* true)

(defmethod client/build-http-request "rest-xml"
  [{:keys [_shapes _operations _metadata] :as service} op-map]
  (rest/build-http-request service
                           op-map
                           (fn [shape-name shape data]
                             (when data
                               (shape/xml-serialize shape
                                                    data
                                                    (or (:locationName shape) shape-name))))))

(defmethod client/parse-http-response "rest-xml"
  [service op-map http-response]
  (rest/parse-http-response service
                            op-map
                            http-response
                            shape/xml-parse
                            common/xml-parse-error))
