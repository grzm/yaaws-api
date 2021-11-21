(ns com.grzm.aws-api.http
  (:import
   (java.net URI)
   (java.net.http HttpClient
                  HttpRequest)))

(def client (HttpClient/newHttpClient))
(def http-request )

(defn build-http-request [_]
  (let [http-request (.build (-> (HttpRequest/newBuilder (URI. "https://www.google.com"))
                                 (.header "host" "some-host")))]
    http-request))
