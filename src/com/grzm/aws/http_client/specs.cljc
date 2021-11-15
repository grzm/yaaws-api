;; Copyright (c) Michael Glaesemann
;; Heavily inspired by congitect.http-client, Copyright (c) Cognitect, Inc.
;; All rights reserved.

(ns com.grzm.aws.http-client.specs
  (:require
   #?(:bb [spartan.spec])
   [clojure.spec.alpha :as s])
  (:import
   (java.nio ByteBuffer)))

(defn- keyword-or-non-empty-string? [x]
  (or (keyword? x)
      (and (string? x) (not-empty x))))

(s/def :com.grzm.aws.http-client/server-name string?)
(s/def :com.grzm.aws.http-client/server-port int?)
(s/def :com.grzm.aws.http-client/uri string?)
(s/def :com.grzm.aws.http-client/request-method keyword?)
(s/def :com.grzm.aws.http-client/scheme keyword-or-non-empty-string?)
(s/def :com.grzm.aws.http-client/timeout-msec int?)
(s/def :com.grzm.aws.http-client/meta map?)
(s/def :com.grzm.aws.http-client/body #(instance? ByteBuffer %))
(s/def :com.grzm.aws.http-client/query-string string?)
(s/def :com.grzm.aws.http-client/headers map?)

(s/def :com.grzm.aws.http-client/submit-request
  (s/keys :req-un [:com.grzm.aws.http-client/server-name
                   :com.grzm.aws.http-client/server-port
                   :com.grzm.aws.http-client/uri
                   :com.grzm.aws.http-client/request-method
                   :com.grzm.aws.http-client/scheme]
          :opt [:com.grzm.aws.http-client/timeout-msec
                :com.grzm.aws.http-client/meta]
          :opt-un [:com.grzm.aws.http-client/body
                   :com.grzm.aws.http-client/query-string
                   :com.grzm.aws.http-client/headers]))

(s/def :com.grzm.aws.http-client/status int?)

(s/def :com.grzm.aws.http-client/submit-http-response
  (s/keys :req-un [:com.grzm.aws.http-client/status]
          :opt [:com.grzm.aws.http-client/meta]
          :opt-un [:com.grzm.aws.http-client/body
                   :com.grzm.aws.http-client/headers]))

(s/def :com.grzm.aws.http-client/error keyword?)
(s/def :com.grzm.aws.http-client/throwable #(instance? Throwable %))

(s/def :com.grzm.aws.http-client/submit-error-response
  (s/keys :req [:com.grzm.aws.http-client/error]
          :opt [:com.grzm.aws.http-client/throwable
                :com.grzm.aws.http-client/meta]))

(s/def :com.grzm.aws.http-client/submit-response
  (s/or :http-response :com.grzm.aws.http-client/submit-http-response
        :error-response :com.grzm.aws.http-client/submit-error-response))
