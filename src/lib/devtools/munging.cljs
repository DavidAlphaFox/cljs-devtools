(ns devtools.munging
  "This namespace implements various heuristics to map Javascript names back to corresponding ClojureScript names.
  The functionality here heavily depends on observed ClojureScript compiler and runtime behaviour (fragile!).
  Answers given by functions in this namespace cannot be perfect because generated Javascript naming schemes produced by
  ClojureScript compiler were not designed with easy reversibility in mind. We recommend this functionality to be used for
  presentation in the UI only. The goal here is to provide user with more familiar view of runtime state of her app
  in most common cases (on best effort basis).

  Our main weapons in this uneven fight are:
    1. munged function names as they appear in Javascript (generated by ClojureScript)
    2. we can also analyze function sources accessible via .toString
    3. special cljs$core$IFn$_invoke protocol props generated for multi-arity functions

  We can also cheat and look at runtime state of browser environment to determine some answers about namespaces.

  If you discovered breakage or a new case which should be covered by this code, please open an issue:
    https://github.com/binaryage/cljs-devtools/issues"
  (:require [clojure.string :as string]
            [devtools.util :refer-macros [oget oset ocall]]))

(declare collect-fn-arities)

(def dollar-replacement "~﹩~")
(def max-fixed-arity-to-scan 64)

; -- helpers ----------------------------------------------------------------------------------------------------------------

(defn get-fn-source-safely [f]
  (try
    (ocall f "toString")
    (catch :default _
      "")))

(defn get-fn-fixed-arity [f n]
  (oget f (str "cljs$core$IFn$_invoke$arity$" n)))

(defn get-fn-variadic-arity [f]
  (oget f (str "cljs$core$IFn$_invoke$arity$variadic")))

(defn get-fn-max-fixed-arity [f]
  (oget f "cljs$lang$maxFixedArity"))

; -- cljs naming conventions ------------------------------------------------------------------------------------------------

(defn cljs-fn-name?
  "Given a Javascript name answers if the name was likely generated by ClojureScript.
  We use a simple heuristic here:
    The name must contain at least two separate dollars because we assume two-segment namespaces."
  [munged-name]
  (if (string? munged-name)
    (some? (re-matches #"^[^$]*\$[^$]+\$.*$" munged-name))))

(defn parse-fn-source
  "Given a function source code parses out [name args]. Note that both strings are still munged.
  Suitable for further processing.

  For exampe for input below the function will return [\"devtools_sample$core$hello\" \"name, unused_param\"]:

  function devtools_sample$core$hello(name, unused_param){
    return [cljs.core.str(\"hello, \"),cljs.core.str(name),cljs.core.str(\"!\")].join('');
  }
  "
  [fn-source]
  (if-let [meat (second (re-find #"function\s(.*?)\{" fn-source))]
    (if-let [match (re-find #"(.*?)\((.*)\)" meat)]
      (rest match))))

(defn trivial-fn-source? [fn-source]
  {:pre [(string? fn-source)]}
  (or (some? (re-matches #"function\s*\(\s*\)\s*\{\s*\}\s*" fn-source))
      (some? (re-matches #"function.*\(\)\s*\{\s*\[native code\]\s*\}\s*" fn-source))))

(defn cljs-fn?
  "Given a Javascript function object returns true if the function looks like a ClojureScript function.

  Uses various heuristics:
    1. must be fn? (is javascript function or satisfies Fn and IFn protocols)
    2. and name must be cljs-fn-name? (name can come from f.name or parsed out of function source)
    3. or if anonymous function, must be non-trivial"
  [f]
  (if (fn? f)
    (let [name (oget f name)]
      (if-not (empty? name)
        (cljs-fn-name? name)
        (let [fn-source (get-fn-source-safely f)]
          (let [[name] (parse-fn-source fn-source)]
            (if-not (empty? name)
              (cljs-fn-name? name)
              (not (trivial-fn-source? fn-source)))))))))                                                                     ; we assume non-trivial anonymous functions to come from cljs

; -- demunging --------------------------------------------------------------------------------------------------------------

(defn dollar-preserving-demunge
  "Standard cljs.core/demunge is too agresive in replacing dollars.
  This wrapper function works around it by leaving dollars intact."
  [munged-name]
  (-> munged-name
      (string/replace "$" dollar-replacement)
      (demunge)
      (string/replace dollar-replacement "$")))

(defn demunge-ns [munged-name]
  (-> munged-name
      (dollar-preserving-demunge)
      (string/replace "$" ".")))

(defn ns-exists? [ns-module-name]
  {:pre [(string? ns-module-name)]}
  (if-let [goog-namespaces (oget js/window "goog" "dependencies_" "nameToPath")]
    (some? (oget goog-namespaces ns-module-name))))

(defn detect-namespace-prefix
  "Given a name broken into namespace parts returns [detected-ns remaining-parts],
  where detected-ns is a string representing longest detected existing namespace and
  remaining-parts is a vector of remaing input parts not included in the detected-ns concatenation.

  For given input [\"cljs\" \"core\" \"first\"] returns [\"cljs.core\" [\"first\"]] (asumming cljs.core exists)"
  [parts]
  (loop [name-parts []
         remaining-parts parts]
    (if (empty? remaining-parts)
      ["" name-parts]
      (let [ns-name (string/join "." remaining-parts)]
        (if (ns-exists? ns-name)
          [ns-name name-parts]
          (recur (concat [(last remaining-parts)] name-parts) (butlast remaining-parts)))))))

(defn break-munged-name
  "Given a munged-name from Javascript lands attempts to break it into a namespace part and remaining short name."
  [munged-name]
  (let [parts (vec (.split munged-name "$"))
        [munged-ns name-parts] (detect-namespace-prefix parts)
        munged-name (string/join "$" name-parts)]
    [munged-ns munged-name]))

(defn break-and-demunge-name
  "Given a munged-name from Javascript lands attempts to brek it into a namespace part and remaining short name.
  Then applies appropriate demunging on them and returns ClojureScript versions of the names."
  [munged-name]
  (let [[munged-ns munged-name] (break-munged-name munged-name)]
    [(demunge-ns munged-ns) (dollar-preserving-demunge munged-name)]))

; -- fn info ----------------------------------------------------------------------------------------------------------------

(defn parse-fn-source-info
  "Given function source code tries to retrieve [ns name & args] on best effort basis, where
  ns is demunged namespace part of the function name (or \"\" if namespace cannot be detected)
  name is demunged short name (or \"\" if function is anonymous or name cannot be retrieved)
  args is optional number of demunged argument names.

  Please note that this function always returns a vector with something. In worst cases [\"\" \"\"].
  "
  [fn-source]
  (if-let [[munged-name args] (parse-fn-source fn-source)]
    (let [[ns name] (break-and-demunge-name munged-name)
          demunged-args (map (comp dollar-preserving-demunge string/trim) (string/split args #","))]
      (concat [ns name] demunged-args))
    ["" ""]))

(defn parse-fn-info
  "Given Javascript function object tries to retrieve [ns name & args] as in parse-fn-source-info (on best effort basis)."
  [f]
  (let [fn-source (get-fn-source-safely f)]
    (parse-fn-source-info fn-source)))

(defn parse-fn-info-deep
  "Given Javascript function object tries to retrieve [ns name & args] as in parse-fn-info (on best effort basis).

  The difference from parse-fn-info is that this function prefers to read args from arities if available.
  It recurses arbitrary deep following IFn protocol leads.

  If we hit multi-arity situation in leaf, we don't attempt to list arguments and return ::multi-arity placeholder instead.

  The reason for reading arities is that it gives more accurate parameter names in some cases.
  We observed that variadic functions don't always contain original parameter names, but individual IFn arity functions do."
  [f]
  (let [fn-info (parse-fn-info f)
        arities (collect-fn-arities f)]
    (if (some? arities)
      (if (> (count arities) 1)
        (concat (take 2 fn-info) ::multi-arity)
        (concat (take 2 fn-info) (drop 2 (parse-fn-info-deep (second (first arities))))))
      fn-info)))

; -- support for human-readable names ---------------------------------------------------------------------------------------

(defn char-to-subscript
  "Given a character with a single digit converts it into a subscript character.
  Zero chracter maps to unicode 'SUBSCRIPT ZERO' (U+2080)."
  [char]
  {:pre [(string? char)
         (= (count char) 1)]}
  (let [char-code (ocall (js/String. char) "charCodeAt" 0)                                                                    ; this is an ugly trick to overcome a V8? bug, char string might not be a real string "object"
        subscript-code (+ 8320 -48 char-code)]                                                                                ; 'SUBSCRIPT ZERO' (U+2080), start with subscript '1'
    (ocall js/String "fromCharCode" subscript-code)))

(defn make-subscript
  "Given a subscript number converts it into a string representation consisting of unicod subscript characters (digits)."
  [subscript]
  {:pre [(number? subscript)]}
  (string/join (map char-to-subscript (str subscript))))

(defn find-index-of-human-prefix
  "Given a demunged ClojureScript parameter name. Tries to detect human readable part and returns the index where it ends.
  Returns nil if no prefix can be detected.

  The idea is to convert macro-generated parameters and other generated names to more friendly names.
  We observed that param names generated by gensym have prefix followed by big numbers.
  Other generated names contain two dashes after prefix (originally probably using underscores)."
  [name]
  (let [sep-start (.indexOf name "--")
        num-prefix (count (second (re-find #"(.*?)\d{2,}" name)))
        finds (filter pos? [sep-start num-prefix])]
    (if-not (empty? finds)
      (apply min finds))))

(defn humanize-name
  "Given a name and intermediate state. Convert name to a human readable version by keeping human readable prefix with
  optional subscribt postfix and store it in ::result. Subscript number is picked based on state. State keeps track of
  previously assigned subscripts. Returns a new state."
  [state name]
  (let [index (find-index-of-human-prefix name)
        prefix (if (> index 0) (.substring name 0 index) name)]
    (if-let [subscript (get state prefix)]
      (-> state
          (update ::result conj (str prefix (make-subscript subscript)))
          (update prefix inc))
      (-> state
          (update ::result conj prefix)
          (assoc prefix 2)))))

(defn humanize-names
  "Given a list of names, returns a list of human-readable versions of those names.
  It detects human-readable prefix using a simple heuristics. When names repeat it assigns simple subscripts starting with 2.
  Subscripts are assigned left-to-right.

  Given [\"p--a\" \"p--b\" \"x\" \"p--c\"] returns [\"p\" \"p₂\" \"x\" \"p₃\"]"
  [names]
  (with-meta (::result (reduce humanize-name {::result []} names)) (meta names)))

; -- arities ----------------------------------------------------------------------------------------------------------------

(defn collect-fn-fixed-arities [f max-arity]
  (loop [arity 0
         collection {}]
    (if (> arity max-arity)
      collection
      (recur (inc arity) (if-let [arity-fn (get-fn-fixed-arity f arity)]
                           (assoc collection arity arity-fn)
                           collection)))))

(defn collect-fn-variadic-arities [f]
  (if-let [variadic-arity (get-fn-variadic-arity f)]
    {::variadic variadic-arity}))

(defn review-arity [[arity arity-fn]]
  (let [sub-arities (collect-fn-arities arity-fn)]
    (if (::variadic sub-arities)
      [::variadic arity-fn]
      [arity arity-fn])))

(defn review-arities
  "Some arities can be marked as fixed arity but in fact point to a variadic-arity function. We want to detect this case
  and turn such improperly categorized arities to ::variadic."
  [arities]
  (if (::variadic arities)
    arities
    (into {} (map review-arity arities))))

(defn collect-fn-arities
  "Given a Javascript function object, tries to inspect known arity properties generated by ClojureScript compiler and
  collects all available arity functions into a map. Arities are keyed by arity count and variadic arity gets ::variadic key."
  [f]
  (let [max-fixed-arity (get-fn-max-fixed-arity f)
        fixed-arities (collect-fn-fixed-arities f (or max-fixed-arity max-fixed-arity-to-scan))                               ; we cannot rely on cljs$lang$maxFixedArity when people implement IFn protocol by hand
        variadic-arities (collect-fn-variadic-arities f)
        arities (review-arities (merge fixed-arities variadic-arities))]
    (if-not (empty? arities)
      arities)))

; -- args lists -------------------------------------------------------------------------------------------------------------

(defn arity-keywords-comparator
  "::variadic goes last, other keywords compare by name."
  [x y]
  (cond
    (= ::variadic x) 1
    (= ::variadic y) -1
    :else (compare (name x) (name y))))

(defn arities-key-comparator
  "numbers go first (ordered), then keywords (ordered by name), and then ::variadic sticks last"
  [x y]
  (let [kx? (keyword? x)
        ky? (keyword? y)]
    (cond
      (and kx? ky?) (arity-keywords-comparator x y)
      kx? 1
      ky? -1
      :else (compare x y))))

(defn arities-to-args-lists*
  [arities]
  (let [sorted-keys (sort arities-key-comparator (keys arities))
        sorted-fns (map #(get arities %) sorted-keys)
        sorted-infos (map parse-fn-info-deep sorted-fns)
        sorted-args-lists (map #(drop 2 %) sorted-infos)]
    (if (= (last sorted-keys) ::variadic)
      (concat (butlast sorted-args-lists) [(vary-meta (last sorted-args-lists) assoc ::variadic true)])
      sorted-args-lists)))

(defn arities-to-args-lists
  "Given a map of arity functions. Tries to parse individual functions and prepare an arguments list for each arity.
  Returned list of arguments list is sorted by arity count, variadic arity goes last if available.

  The function also optionally humanizes argument names in each arguments list if requested."
  [arities & [humanize?]]
  (let [args-lists (arities-to-args-lists* arities)]
    (if humanize?
      (map humanize-names args-lists)
      args-lists)))

; -- UI presentation --------------------------------------------------------------------------------------------------------

(defn args-lists-to-strings
  "Converts a list of arguments lists into a list of strings suitable for UI presentation."
  [args-lists spacer-symbol multi-arity-symbol rest-symbol]
  (let [string-mapper (fn [arg]
                        (case arg
                          ::multi-arity multi-arity-symbol
                          arg))
        printer (fn [args-list]
                  (let [variadic? (::variadic (meta args-list))
                        args-strings (map string-mapper args-list)]
                    (str (string/join spacer-symbol (butlast args-strings))
                         (if variadic? rest-symbol spacer-symbol)
                         (last args-strings))))]
    (->> args-lists
         (map printer)
         (map string/trim))))

(defn extract-args-strings [f humanize? spacer-symbol multi-arity-symbol rest-symbol]
  (-> (or (collect-fn-arities f) {:naked f})
      (arities-to-args-lists humanize?)
      (args-lists-to-strings spacer-symbol multi-arity-symbol rest-symbol)))
