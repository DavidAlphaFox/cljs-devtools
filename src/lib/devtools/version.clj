(ns devtools.version)

(def current-version "1.0.5")                                                                                        ; this should match our project.clj

(defmacro get-current-version []
  current-version)
