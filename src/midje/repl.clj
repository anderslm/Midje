(ns ^{:doc "Functions useful when using Midje in the repl or from the command line.
            See `midje-repl-help` for details."}
  midje.repl
  (:use [bultitude.core :only [namespaces-in-dir namespaces-on-classpath]]
        clojure.pprint)
  (:require midje.sweet
            [midje.ideas.facts :as fact]
            [midje.internal-ideas.compendium :as compendium]
            [midje.ideas.reporting.levels :as levelly]
            [midje.ideas.metadata :as metadata]
            [midje.doc :as doc]
            [leiningen.core.project :as project]
            [midje.util.form-utils :as form]
            [midje.util.namespace :as namespace]))

(namespace/immigrate-from 'midje.ideas.metadata
                          (map metadata/metadata-function-name
                               metadata/fact-properties))

(when (doc/appropriate?)
  (namespace/immigrate-from 'midje.doc doc/for-repl)
  (doc/repl-notice))

(when-not (ns-resolve 'user '=>) ; when not already `use`d.
  (namespace/immigrate 'midje.sweet))



                                ;;; Miscellaneous utilities


(defn- check-facts-once-given [fact-functions]
  (levelly/forget-past-results)
  (let [results (doall (map fact/check-one fact-functions))]
    (levelly/report-summary)
    (every? true? results)))
  

                                ;;; Loading facts from the repl

(defn- ^{:testable true} paths-to-load []
  (try
    (let [project (project/read)]
      (concat (:test-paths project) (:source-paths project)))
    (catch java.io.FileNotFoundException e
      ["test"])))

(defn- ^{:testable true} expand-namespaces [namespaces]
  (mapcat #(if (= \* (last %))
             (namespaces-on-classpath :prefix (apply str (butlast %)))
             [(symbol %)])
          (map str namespaces)))


(declare forget-facts)
(defn load-facts*
  "Functional form of `load-facts`."
  [args]
  (levelly/obeying-print-levels [args args]
    (let [desired-namespaces (if (empty? args)
                               (mapcat namespaces-in-dir (paths-to-load))
                               (expand-namespaces args))]
    (levelly/forget-past-results)
    (doseq [ns desired-namespaces]
      (forget-facts ns)
      ;; Following strictly unnecessary, but slightly useful because
      ;; it reports the changed namespace before the first fact loads.
      ;; That way, some error in the fresh namespace won't appear to
      ;; come from the last-loaded namespace.
      (levelly/report-changed-namespace ns)
      (require ns :reload))
    (levelly/report-summary)
    nil)))

(defmacro load-facts 
  "Load given namespaces, as in:
     (load-facts midje.t-sweet midje.t-repl)
   Note that namespace names need not be quoted.

   If no namespaces are given, all the namespaces in the project.clj's
   :test-paths and :source-paths will be loaded.
   But if there's no project.clj, all namespaces under \"test\"
   will be loaded.

   A partial namespace ending in a `*` will load all sub-namespaces.
   Example: (load-facts midje.ideas.*)

   By default, all facts are loaded from the namespaces. You can, however,
   add further arguments. Only facts matching one or more of the arguments
   are loaded. The arguments are:

   :keyword      -- Does the metadata have a truthy value for the keyword?
   \"string\"    -- Does the fact's name contain the given string? 
   #\"regex\"    -- Does any part of the fact's name match the regex?
   a function    -- Does the function return a truthy value when given
                    the fact's metadata?

   In addition, you can adjust what's printed. See `(print-level-help)`."
  [& args]
  (let [error-fixed (map #(if (form/quoted? %) (second %) %)
                         args)]
    `(load-facts* '~error-fixed)))


                                ;;; Fetching facts

(defn fetch-facts
  "Fetch facts that have already been defined, whether by loading
   them from a file or via the repl.

   (fetch-facts)                  -- defined in the current namespace
   (fetch-facts *ns* midje.t-repl -- defined in named namespaces
                                     (Names need not be quoted.)
   (fetch-facts :all)             -- defined anywhere

   You can further filter the facts by giving more arguments. Facts matching
   any of the arguments are included in the result. The arguments are:

   :keyword      -- Does the metadata have a truthy value for the keyword?
   \"string\"    -- Does the fact's name contain the given string? 
   #\"regex\"    -- Does any part of the fact's name match the regex?
   a function    -- Does the function return a truthy value when given
                    the fact's metadata?"

  [& args]
  (let [args (if (empty? args) [*ns*] args)]
    (mapcat (fn [arg]
              (cond (metadata/describes-name-matcher? arg)
                    (filter (metadata/name-matcher-for arg)
                            (compendium/all-facts<>))
                    
                    (= arg :all)
                    (compendium/all-facts<>)
                    
                    (metadata/describes-callable-matcher? arg)
                    (filter (metadata/callable-matcher-for arg)
                            (compendium/all-facts<>))
                    
                    :else
                    (compendium/namespace-facts<> arg)))
            args)))

                                ;;; Forgetting facts


(defn forget-facts 
  "Forget defined facts so that they will not be found by `check-facts`
   or `fetch-facts`.

   (forget-facts)                  -- defined in the current namespace
   (forget-facts *ns* midje.t-repl -- defined in named namespaces
                                      (Names need not be quoted.)
   (forget-facts :all)             -- defined anywhere

   You can further filter the facts by giving more arguments. Facts matching
   any of the arguments are the ones that are forgotten. The arguments are:

   :keyword      -- Does the metadata have a truthy value for the keyword?
   \"string\"    -- Does the fact's name contain the given string? 
   #\"regex\"    -- Does any part of the fact's name match the regex?
   a function    -- Does the function return a truthy value when given
                    the fact's metadata?"

  [& args]
  (let [args (if (empty? args) [*ns*] args)]
    (doseq [arg args]
      (cond (= arg :all)
            (compendium/fresh!)

            (or (string? arg)
                (form/regex? arg)
                (fn? arg)
                (keyword? arg))
            (doseq [fact (fetch-facts arg)]
              (compendium/remove-from! fact))
                    
            :else
            (compendium/remove-namespace-facts-from! arg)))))


                                ;;; Checking facts

(def ^{:doc "Check a single fact. Takes as its argument a function such
    as is returned by `last-fact-checked`."}
  check-one-fact fact/check-one)

(defn check-facts
  "Check facts that have already been defined.

   (check-facts)                  -- defined in the current namespace
   (check-facts *ns* midje.t-repl -- defined in named namespaces
                                     (Names need not be quoted.)
   (check-facts :all)             -- defined anywhere

   You can further filter the facts by giving more arguments. Facts matching
   any of the arguments are the ones that are checked. The arguments are:

   :keyword      -- Does the metadata have a truthy value for the keyword?
   \"string\"    -- Does the fact's name contain the given string? 
   #\"regex\"    -- Does any part of the fact's name match the regex?
   a function    -- Does the function return a truthy value when given
                    the fact's metadata?

   In addition, you can adjust what's printed. See `(print-level-help)`."
  [& args]
  (levelly/obeying-print-levels [args args]
    (let [fact-functions (apply fetch-facts args)]
    (check-facts-once-given fact-functions))))
    

                                ;;; The history of checked facts

(defn last-fact-checked
  "The last fact or tabular fact that was checked. Only top-level
   facts are recorded, not facts nested within them."
  []
  (compendium/last-fact-checked<>))

(defn source-of-last-fact-checked 
  "Returns the source of the last fact or tabular fact run."
  []
  (fact-source (last-fact-checked)))

(defn recheck-fact 
  "Recheck the last fact or tabular fact that was checked.
   When facts are nested, the entire outer-level fact is rechecked.
   The result is true if the fact checks out.

   You can adjust what's printed. See `(print-level-help)`."
  ([]
     (check-facts-once-given [(last-fact-checked)]))
  ([print-level]
     (levelly/obeying-print-levels [print-level] (recheck-fact))))

(def ^{:doc "Synonym for `recheck-fact`."} rcf recheck-fact)
