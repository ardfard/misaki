(ns misaki.core
  "Misaki Controll Core"
  (:use misaki.config
        [misaki.util file string sequence notify]
        [text-decoration.core :only [green]]
        [pretty-error.core    :only [print-pretty-stack-trace]])
  (:import [java.io File]))

;; ## Util

(defn key->sym [k] (symbol (name k)))

;; ## Compiler Utilities

; =stop-compile?
(defn- stop-compile? [compile-result]
  (and (map?  compile-result)
       (true? (:stop-compile? compile-result))))

; =all-compile?
(defn- all-compile? [compile-result]
  (and (map?  compile-result)
       (true? (:all-compile? compile-result))))

; =skip-compile?
(defn- skip-compile? [compile-result]
  (or (symbol? compile-result)
      (and (map?    compile-result)
           (symbol? (:status compile-result))
           (every?  #(not (% compile-result)) [stop-compile? all-compile?]))))

; =call-compiler-fn
(defn- call-compiler-fn
  "Call compiler's function.

      (call-compiler-fn :-compile file)
      (let [compiler {'-extension #(list :clj %)}]
        (call-compiler-fn compiler :-extension :cljs))
  "
  [& params]
  (let [[compiler fn-key & args] (if (-> params first map?)
                                   params
                                   (cons (:compiler *config*) params))
        fn-sym (key->sym fn-key)]
    (if (sequential? compiler)
      (mapcat #(apply call-compiler-fn % fn-sym args) compiler)
      (if-let [f (get compiler fn-sym)] (apply f args)))))

; =get-watch-file-extensions
(defn get-watch-file-extensions
  "Get extensions list to watch."
  []
  (map normalize-extension (distinct (call-compiler-fn :-extension))))

; =handleable-compiler?
(defn handleable-compiler?
  [compiler file]
  (let [exts (call-compiler-fn compiler :-extension)]
    (not (nil? (some #(has-extension? % file) exts)))))

; =get-template-files
(defn get-template-files
  "Get all template files. Find specified directory with `:dir` option."
  [& {:keys [dir] :or {dir (:template-dir *config*)}}]
  (let [exts (get-watch-file-extensions)]
    (filter
      (fn [file]
        (some #(and (.isFile file) (has-extension? % file)) exts))
      (find-files dir))))

; =get-post-files
(defn get-post-files
  "Get all post files.
  Sort file list with `:sort?` option(default setting is FALSE)."
  [& {:keys [sort?] :or {sort? false}}]

  (let [files (get-template-files :dir (:post-dir *config*))]
    (if sort?
      ((sort-type->sort-fn) files)
      files)))


;; ## Compiler Functions

; =update-config
(defn update-config
  "Update basic config with plugin's -config function."
  [& [compiler]]
   (let [config (make-basic-config-map)
         res    (if compiler
                  (call-compiler-fn compiler :-config config)
                  (let [compilers (flatten (list (:compiler *config*)))]
                    (if (= 1 (count compilers))
                      (call-compiler-fn (first compilers) :-config config)
                      (map #(call-compiler-fn % :-config config) compilers))))]
     (if res res config)))

; =process-compile-result
(defn process-compile-result
  "Process compile result and return process result.

  * string?
    * Write file with default filename.
  * true? or false? or symbol?
    * Do nothing.
      * `true`  : Success
      * `false` : Fail
      * `symbol`: Skip
  * map?
    * Write file with detailed setting.
      * `:status`  : True(success), false(fail) or something else(skip)
      * `:filename`: Filename to write
      * `:body`    : Compiled body text. if body is nil, only status is checked
  "
  [result default-filename]
  (cond
    ; output with default filename
    (string? result)
    (if default-filename
      (do (write-file (add-public-dir default-filename) result)
          true)
      false)

    ; only check status
    (or (true? result) (false? result))
    result

    ; output with detailed data
    (map? result)
    (let [{:keys [status filename body]
           :or   {status false
                  filename default-filename}} result]
      (if (and filename body)
        (do (write-file (add-public-dir filename) body)
            status)
        status))

    ; error
    :else false))

; =compile*
(defn compile*
  "Common function to compile java.io.File with config."
  ([file] (compile* {} file))
  ([optional-config file]
   (try
     (some-with-default-value
       #(if (handleable-compiler? % file)
          (let [config         (merge (update-config %) optional-config)
                compile-result (call-compiler-fn % :-compile config file)
                process-result (process-compile-result
                                 compile-result
                                 (make-output-filename file))]
            (when-not (skip-compile? compile-result)
              (if (:notify? *config*) (notify-result file process-result))
              [process-result compile-result])))
       (flatten (list (:compiler *config*)))
       [true 'skip])

     (catch Exception e
       (print-pretty-stack-trace
         e :filter #(str-contains? (:str %) "misaki"))
       ; notify error
       (if (:notify? *config*) (notify-result file false e))
       [false {:stop-compile? true}]))))

; =call-all-compile
(defn call-all-compile
  "Call plugin's -compile function for all template files."
  []
  (loop [[file & rest-files] (get-template-files), success? true]
    (if file
      (let [[process-result compile-result] (compile* {:-compiling :all} file)
            result (and success? (or (symbol? process-result)
                                     (true? process-result)))]
        (if (stop-compile? compile-result)
          result
          (recur rest-files result)))
      success?)))

; =call-compile
(defn call-compile
  "Call plugin's -compile function."
  [file]
  (let [[process-result compile-result] (compile* {:-compiling :single} file)]
    (cond
      ; all compile
      (all-compile? compile-result)
      (do (println (green "   Switch to all compiling"))
          (call-all-compile))

      ; compile with post
      (post-file? file)
      (every? #(true? (first %))
              (for [file (map template-name->file (:compile-with-post *config*))]
                (compile* {:-compiling :single} file)))

      :else process-result)))

