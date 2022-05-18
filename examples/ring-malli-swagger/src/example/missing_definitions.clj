(ns example.missing-definitions
  (:require [reitit.ring :as ring]
            [reitit.coercion.malli]
            [reitit.ring.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [muuntaja.core :as m]
            [malli.core :as mc]
            [malli.util :as mu]
            [malli.registry :as mr]
            [malli.swagger :as mswagger]))

(def my-reg
  {::cons [:maybe [:tuple pos-int? [:ref ::cons]]]})

(def thing
  [:schema {:registry my-reg} ::cons])

(def coercer
  (reitit.coercion.malli/create
   {:options {:registry (mr/registry (merge (mc/default-schemas) my-reg))}}))

(comment
  ;; These work from REPL, but not from when invoked from the web,
  ;; they raise Resolver error at paths./foo.post.parameters.0.schema.$ref
  (mc/schema thing)
  (mswagger/transform thing)
  (mc/validate thing [1 [2 [3 nil]]])
  (mc/explain thing [1 [2 [3 "four"]]]))

(def app
  (ring/ring-handler
    (ring/router
      [["/swagger.json"
        {:get {:no-doc true
               :swagger {:info {:title "Missing Definitions Example"
                                :description "with [malli](https://github.com/metosin/malli) and reitit-ring"}}
               :handler (swagger/create-swagger-handler)}}]

       ["/foo"
        {:swagger {:tags ["foo"]}
         :post {:summary "refers to recursive schema"
                :coercion coercer
                :parameters {:body thing}
                :handler (fn [_] {:status 200 :body {}})}}]]

      {:exception pretty/exception
       :data {:coercion coercer
              :muuntaja m/instance
              :middleware [swagger/swagger-feature
                           parameters/parameters-middleware
                           muuntaja/format-negotiate-middleware
                           muuntaja/format-response-middleware
                           exception/exception-middleware
                           muuntaja/format-request-middleware
                           coercion/coerce-response-middleware
                           coercion/coerce-request-middleware
                           multipart/multipart-middleware]}})
    (ring/routes
      (swagger-ui/create-swagger-ui-handler
        {:path "/"
         :config {:validatorUrl nil
                  :operationsSorter "alpha"}})
      (ring/create-default-handler))))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "server running in port 3000"))

(comment
  (start))
