# yaaws-api

**Alpha** http-client does not yet work with babashka (see Issues)

yaaws-api is _yet another_ Clojure library which provides programmatic
access to AWS services from your Clojure program. Its _raison d'être_
is to be a drop-in replacement for Cognitect's brilliant [aws-api][] that
will work from source with babashka.

yaaws-api should work with com.cognitect.aws/endpoints and
com.cognitect.aws service packages.

* [aws-api][]
* [API Docs](https://cognitect-labs.github.io/aws-api/)

[aws-api]: https://github.com/cognitect-labs/aws-api

## In brief

Add to your `deps.edn`

```clojure
{:deps {
   com.grzm.aws/yaapi {:git/sha "0edb68904cd5a08b424d1a178d437d5ea4ac526e"}
}}
```

```clojure
(require '[com.grzm.aws.client.api :as aws])
(def sts (aws/client {:api :sts}))
(keys (aws/invoke sts {:op :GetCallerIdentity))) ;; => (:UserId :Account :Arn)
```

## Differences from aws-api

 - Uses cheshire instead of clojure.data.json when running under babashka.
 - Includes only a single http-client implementation based on java.net.http
 - Does not auto-refresh AWS credentials as the aws-api relies on
   java.util.concurrent.Executors and friends which aren't included in babashka.

## Issues
 - The aws-api needs to include a `host` header but
   java.net.http.HttpRequest considers `host` a restricted
   header. This can be overridden with the Java System property
   `jdk.httpclient.allowRestrictedHeaders=host`. This works only in
   jdk versions >= 12. This works when running on the JVM, but doesn't
   appear to work with babashka.
 - The aws-api uses for `javax.crypto.Mac` and
   `javax.crypto.spec.SecretKeySpec` for determining
   hmac-sha-256. These classes which aren't currently included in
   babahska.  It does work with a custom babashka build that includes
   these classes. See https://github.com/babashka/babashka/pull/1066
   for a build with the classes included.

## Copyright and License

Parts © 2021 Michael Glaesemann
Mostly © 2015 Cognitect

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
