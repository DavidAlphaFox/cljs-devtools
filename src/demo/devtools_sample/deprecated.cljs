(ns devtools-sample.deprecated
  (:require-macros [devtools-sample.logging :refer [log info]])
  (:require [devtools-sample.boot :refer [boot!]]
            [devtools.protocols :refer [IFormat]]
            [devtools.format :as format]
            [clojure.string :as string]))

(boot! "/src/demo/devtools_sample/deprecated.cljs")

(enable-console-print!)

;(defn envelope
;  "This is a simple wrapper for logging \"busy\" objects.
;  This is especially handy when you happen to be logging javascript objects with many properties.
;  Standard javascript console renderer tends to print a lot of infomation in the header in some cases and that can make
;  console output pretty busy. By using envelope you can force your own short header and let the details expand on demand
;  via a disclosure triangle. The header can be styled and you can optionally specify preferred wrapping tag (advanced)."
;  ([obj]
;   (envelope obj :default-envelope-header))
;  ([obj header]
;   (envelope obj header :default-envelope-style))
;  ([obj header style]
;   (envelope obj header style "span"))
;  ([obj header style tag]
;   (reify
;     IFormat
;     (-header [_] (format/make-template tag style (if (fn? header) (header obj) header)))
;     (-has-body [_] true)
;     (-body [_] (format/make-template :span :body-style (format/standard-reference obj))))))
;
;(defn force-format
;  "Forces object to be rendered by cljs-devtools during console logging.
;  Unfortunately custom formatters subsystem in DevTools is not applied to primitive values like numbers, strings, null, etc.
;  This wrapper can be used as a workaround if you really need to force cljs-devtools rendering:
;    (.log js/console nil) ; will render 'null'
;    (.log js/console (force-format nil)) ; will render 'nil' and not 'null'
;  See https://github.com/binaryage/cljs-devtools/issues/17
;  "
;  [obj]
;  (format/make-surrogate obj (format/build-header obj) false))
;
;(deftype Person [name address]
;  format/IDevtoolsFormat
;  (-header [_] (format/template "span" "color:white;background-color:#999;padding:0px 4px;" (str "Person: " name)))
;  (-has-body [_] (some? address))
;  (-body [_] (format/standard-body-template (string/split-lines address))))

; --- MEAT STARTS HERE -->

;(log "!" (envelope "string" "header"))
;(log (Person. "John Doe" "Office 33\n27 Colmore Row\nBirmingham\nEngland") (Person. "Mr Homeless" nil))

; <-- MEAT STOPS HERE ---
