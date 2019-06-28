(defproject naga-http "0.1.0-SNAPSHOT"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojars.quoll/naga "0.2.22"]
                 [org.clojars.quoll/naga-store "0.3.1"]
                 [org.clojars.quoll/asami "0.3.3"]
                 [org.clojars.quoll/appa "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.logging "0.4.1"]
                 [log4j "1.2.17"]
                 [threatgrid/clj-momo "0.2.31"]
                 [prismatic/schema "1.1.11"]
                 [compojure "1.6.1"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring-json-response "0.2.0"]
                 [ring-middleware-format "0.7.4"]]
  :resource-paths ["resources"]
  :classpath ".:resources"
  :plugins [[lein-ring "0.12.5"]]
  :ring {:nrepl {:start? true}
         :handler naga-http.server/app}
  :main naga-http.server
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.4.0"]]}})
