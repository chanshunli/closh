(ns closh.zero.frontend.sci
  (:gen-class)
  (:require
   #_[edamame.core :as edamame]
   [closh.zero.compiler]
   [closh.zero.parser :as parser]
   [closh.zero.pipeline]
   [closh.zero.platform.eval :as eval]
   [closh.zero.platform.process :as process]
   [closh.zero.env :as env]
   [closh.zero.reader :as reader]
   [closh.zero.env :as env]
   [clojure.tools.reader.reader-types :as r]
   [closh.zero.utils.clojure-main :as clojure-main]))

(defn repl-print
  [result]
  (when-not (or (nil? result)
                (identical? result env/success)
                (process/process? result))
    (if (or (string? result)
            (char? result))
      (print result)
      (pr result))
    (flush)))

(defn repl-opt
  [[_ & args] inits]
  (clojure-main/repl
    :init (fn []
            ;;(clojure-main/initialize args inits)
            ;;(apply require repl-requires)
            ;;(eval/eval-closh-requires)
            (eval/eval env/*closh-environment-init*))
    :read
      (let [in (r/indexing-push-back-reader (r/push-back-reader *in*))]
        (fn [request-prompt request-exit]
          `(-> ~(closh.zero.compiler/compile-interactive
                 (closh.zero.parser/parse
                  (reader/read in)))
               (closh.zero.pipeline/wait-for-pipeline))))
    :eval eval/eval
    :print repl-print)
  (prn)
  (System/exit 0))

(defn -main [& args]
  (reset! process/*cwd* (System/getProperty "user.dir"))
  (if-some [cmd (first args)]
    (repl-print
     (eval/eval
      `(-> ~(closh.zero.compiler/compile-interactive
             (closh.zero.parser/parse
              (reader/read-string cmd)
              #_(edamame/parse-string-all cmd {:all true})))
           (closh.zero.pipeline/wait-for-pipeline))))
    (repl-opt nil nil)))
