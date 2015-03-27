(defproject cljs-devtools-sample "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3126"]
                 [binaryage/devtools "0.1.2"]
                 [cljs-http "0.1.25"]
                 [ring "1.3.2"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-ring "0.9.1"]]

  :clean-targets ["out"]

  :ring {:handler server.core/app}

  :source-paths ["src" "target/classes"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src" "checkouts/cljs-devtools/src"]
              :compiler {
                :main cljs-devtools-sample.core
                :output-to "out/cljs_devtools_sample.js"
                :output-dir "out"
                :optimizations :none
                :cache-analysis true
                :source-map true}}]})
