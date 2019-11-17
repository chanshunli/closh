(ns closh.zero.utils.sci
  (:require [sci.core :as sci]
            [sci.impl.interpreter :as interpreter]
            [closh.zero.pipeline :as pipeline]
            [closh.zero.core :as closh-core]
            [closh.zero.platform.process :as process]
            [closh.zero.builtin :as builtin]
            [closh.zero.env :as env]
            [closh.zero.util :refer [thread-stop]]
            [closh.zero.macros-fns :as macros-fns]
            [clojure.repl :as repl]))

(set! *warn-on-reflection* true)

(comment
  (defmacro closh-requires []
    closh.zero.env/*closh-environment-requires*)

  (defmacro closh-bindings []
    (->> closh.zero.env/*closh-environment-requires*
         (drop 1)
         (mapcat (fn [[_ [namespace & opts]]]
                   (when (not= namespace 'closh.zero.macros)
                     (let [{:keys [as refer]} (apply hash-map opts)]
                       (concat
                        (for [x refer]
                          [x (symbol (str namespace) (str x))])
                        (->>
                         (ns-publics namespace)
                         (keys)
                         (map (fn [k]
                                [(symbol (str namespace) (str k))
                                 (symbol (str namespace) (str k))])))
                        (when as
                          (->>
                           (ns-publics namespace)
                           (keys)
                           (map (fn [k]
                                  [(symbol (str as) (str k))
                                   (symbol (str as) (str k))])))))))))
         (map (fn [[k v]]
                [(list 'quote k) v]))
         (into {})))

  (defmacro closh-macro-bindings []
    (with-open [rdr (io/reader "src/common/closh/zero/macros.cljc")]
      (let [prdr (PushbackReader. rdr)
            eof (Object.)
            opts {:eof eof :read-cond :allow :features #{:clj}}]
        (loop [bindings {}]
          (let [form (reader/read opts prdr)]
            (cond (= form eof) bindings

                  (= (first form) 'defmacro)
                  (let [name (second form)
                        fn-form `(with-meta
                                   (fn ~name ~@(drop-while #(not (vector? %)) form))
                                   {:sci/macro true})]
                    (recur (assoc bindings
                                  (list 'quote name) fn-form
                                  (list 'quote (symbol "closh.zero.macros" (str name))) fn-form)))

                  :else (recur bindings)))))))

  (closh-requires)

  #_(def bindings
      (merge
       (closh-bindings)
       (closh-macro-bindings)
       {})))
       ;; 'thread-stop thread-stop
       ;; 'clojure.repl/set-break-handler! clojure.repl/set-break-handler!
       ;; 'closh.zero.env/*closh-commands* closh.zero.env/*closh-commands*


(declare ctx)

(defn load-file* [file]
  (let [s (slurp file)]
    (sci/eval-string s ctx)))

(def sci-env (atom {}))

(def macro-bindings
  {'sh (with-meta (fn [_ _ & args] (apply macros-fns/sh args)) {:sci/macro true})
   'sh-value (with-meta (fn [_ _ & args] (apply macros-fns/sh-value args)) {:sci/macro true})
   'sh-val (with-meta (fn [_ _ & args] (apply macros-fns/sh-val args)) {:sci/macro true})
   'sh-str (with-meta (fn [_ _ & args] (apply macros-fns/sh-str args)) {:sci/macro true})
   'sh-seq (with-meta (fn [_ _ & args] (apply macros-fns/sh-seq args)) {:sci/macro true})
   'sh-lines (with-meta (fn [_ _ & args] (apply macros-fns/sh-lines args)) {:sci/macro true})
   'sh-code (with-meta (fn [_ _ & args] (apply macros-fns/sh-code args)) {:sci/macro true})
   'sh-ok (with-meta (fn [_ _ & args] (apply macros-fns/sh-ok args)) {:sci/macro true})
   'sh-wrapper (with-meta (fn [_ _ & args] (apply macros-fns/sh-wrapper args)) {:sci/macro true})
   'defalias (with-meta (fn [_ _ & args] (apply macros-fns/defalias args)) {:sci/macro true})
   'defabbr (with-meta (fn [_ _ & args] (apply macros-fns/defabbr args)) {:sci/macro true})
   'defcmd (with-meta (fn [_ _ & args] (apply macros-fns/defcmd args)) {:sci/macro true})})

(def bindings {'deref deref
               'clojure.core/deref deref
               'swap! swap!
               'clojure.core/swap! swap!
               'print print
               'println println
               'load-file load-file*
               'Math/sqrt #(Math/sqrt %)
               'java.lang.Thread/currentThread #(Thread/currentThread)
               'thread-stop thread-stop
               'clojure.repl/set-break-handler! repl/set-break-handler!
               'closh.zero.env/*closh-commands* env/*closh-commands*
               'cd builtin/cd
               'exit builtin/exit
               'quit builtin/quit
               'getenv builtin/getenv
               'setenv builtin/setenv
               'unsetenv builtin/unsetenv})

(def ctx {:bindings (merge bindings macro-bindings)
          :namespaces {'closh.zero.macros macro-bindings
                       'clojure.core {'println println
                                      'print print}
                       'closh.zero.pipeline {'pipe pipeline/pipe
                                             'redir pipeline/redir
                                             'wait-for-pipeline pipeline/wait-for-pipeline
                                             'pipeline-condition pipeline/pipeline-condition
                                             'pipe-multi pipeline/pipe-multi
                                             'process-output pipeline/process-output}
                       'closh.zero.platform.process {'exit-code process/exit-code
                                                     'wait process/wait
                                                     'cwd process/cwd}
                       'closh.zero.core {'expand-variable closh-core/expand-variable
                                         'expand-tilde closh-core/expand-tilde
                                         'expand-filename closh-core/expand-filename
                                         'expand-redirect closh-core/expand-redirect
                                         'expand-partial closh-core/expand-partial
                                         'expand closh-core/expand
                                         'expand-command closh-core/expand-command
                                         'get-command-suggestion closh-core/get-command-suggestion
                                         'shx closh-core/shx
                                         'expand-alias closh-core/expand-alias
                                         'expand-abbreviation closh-core/expand-abbreviation}}
          :env sci-env})

(defn sci-eval [form]
  ;; (prn "EVAL FORM" form)
  ;; (sci/eval-string (pr-str form) ctx)
  (let [ctx (interpreter/opts->ctx ctx)]
    (interpreter/eval-edn-vals ctx [form])))
