(ns com.grzm.aws.http-kit
  (:require
   [clojure.core.async :refer [put!] :as a]
   [com.grzm.aws.http-client.client :as client]
   [org.httpkit.client :as httpkit.client]
   [org.httpkit.sni-client :as sni-client])
  (:import
   (java.nio ByteBuffer)))

(set! *warn-on-reflection* true)

(defn submit
  "Submit an http request, channel will be filled with response. Returns ch.

  Request map:

  :server-name        string
  :server-port         integer
  :uri                string
  :query-string       string, optional
  :request-method     :get/:post/:put/:head
  :scheme             :http or :https
  :headers            map from downcased string to string
  :body               ByteBuffer, optional
  :cognitect.http-client/timeout-msec   opt, total request send/receive timeout
  :cognitect.http-client/meta           opt, data to be added to the response map

  content-type must be specified in the headers map
  content-length is derived from the ByteBuffer passed to body

  Response map:

  :status              integer HTTP status code
  :body                ByteBuffer, optional
  :header              map from downcased string to string
  :cognitect.http-client/meta           opt, data from the request

  On error, response map is per cognitect.anomalies"
  ([client request]
   (submit client request (a/chan 1)))
  ([client request ch]
   (client/submit client request ch)))

(defn map->http-request
  [{:keys [scheme server-name server-port uri query-string
           request-method headers body]
    :or {scheme "https"}
    :as m}]
  (let [url (str (name scheme)
                 "://"
                 server-name
                 (some->> server-port (str ":"))
                 uri
                 (some->> query-string (str "?")))]
    (cond-> {:url url
             :method request-method
             :headers headers
             :follow-redirects true
             :as :byte-array}
      (::timeout-msecs m) (assoc :timeout (::timeout-msecs m))
      body (assoc :body body))))

(defn error->anomaly [^Throwable t]
  {:cognitect.anomalies/category :cognitect.anomalies/fault
   :cognitect.anomalies/message (.getMessage t)
   ::throwable t})

(defn response-map
  [{:keys [status body headers] :as _response}]
  (cond-> {:status status
           :headers headers}
    body (assoc :body (ByteBuffer/wrap body))))

(defrecord Client
    [http-client pending-ops pending-ops-limit connect-timeout-msecs]
  client/Client
  (-submit [_ request ch]
    (if (< pending-ops-limit (swap! pending-ops inc))
      (do
        (put! ch (merge {:cognitect.anomalies/category :cognitect.anomalies/busy
                         :cognitect.anomalies/message (str "Ops limit reached: " pending-ops-limit)
                         :pending-ops-limit pending-ops-limit}
                        (select-keys request [::meta])))
        (swap! pending-ops dec))
      (try
        (let [http-request (assoc (map->http-request request)
                                  :client http-client
                                  :connect-timeout connect-timeout-msecs)]
          (-> (httpkit.client/request
                http-request
                (fn [{:keys [error] :as response}]
                  (let [res (if error
                              {:cognitect.anomalies/category :cognitect.anomalies/fault
                               :error error}
                              (merge (response-map response)
                                     (select-keys request [::meta])))]
                    (put! ch res))))))
        (catch Throwable t
          (put! ch (merge (error->anomaly t) (select-keys request [::meta])))
          (swap! pending-ops dec))))
    ch))

(defn create
  [{:keys [connect-timeout-msecs
           pending-ops-limit]
    :or {pending-ops-limit 64
         connect-timeout-msecs 5000}
    :as _config}]
  (let [http-client (httpkit.client/make-client {:ssl-configurer sni-client/ssl-configurer})]
    (->Client http-client (atom 0) pending-ops-limit connect-timeout-msecs)))

(defn stop
  "no-op. Implemented for compatibility"
  [^Client _client])
