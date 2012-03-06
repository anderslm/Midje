(ns ^{:doc "Midje's special blend of generative-style testing."}
  midje.ideas.formulas
  (:use [midje.util.form-utils :only [pop-docstring]]
        [midje.error-handling.validation-errors :only [simple-report-validation-error validate when-valid]]
        [midje.ideas.arrows :only [leaves-contain-arrow?]]))

(def ^{:doc "The number of facts generated per formula."
       :dynamic true} 
  *num-generations-per-formula* 100)   

(set-validator! #'*num-generations-per-formula* 
  (fn [new-val]
    (if (pos? new-val) 
      true
      (throw (RuntimeException. (str "*num-generations-per-formula* must be an integer 1 or greater. You tried to set it to: " new-val))))))

(defmacro formula 
  "ALPHA/EXPERIMENTAL - Generative-style fact macro. 
  
  Ex. (formula \"any two strings concatenated begins with the first\" 
        [a (gen/string) b (gen/string)] 
        (str a b) => (has-prefix a))
        
  Currently, we recommend you use generators from test.generative.generators
  
  The midje.ideas.formulas/*num-generations-per-formula* dynamic var determines
  how many facts are generated per formula."
  {:arglists '([docstring? bindings & body])}
  [& args]
  (when-valid &form
    (let [[docstring? [bindings & body]] (pop-docstring args)
          fact `(let ~bindings
                  (midje.sweet/fact ~docstring?
                    ~@body :formula :formula-in-progress ))
          conclusion-signal `(midje.sweet/fact
                                :always-pass midje.sweet/=> :always-pass 
                                :formula :formula-conclude )]

      `(try
         (loop [cnt-down# *num-generations-per-formula*]
           (when (and (pos? cnt-down#) ~fact)
             (recur (dec cnt-down#))))
         (finally
           ~conclusion-signal)))))

(defmethod validate "formula" [[_formula_ & args :as form]]
  (cond (not (leaves-contain-arrow? args))
        (simple-report-validation-error form "There is no arrow in your formula form:")

        (let [[_ [bindings & _]] (pop-docstring args)]
          (or (not (vector? bindings))
              (odd? (count bindings))
              (< (count bindings) 2)))
        (simple-report-validation-error form "Formula requires bindings to be an even numbered vector of 2 or more:")
  
        :else 
        args))