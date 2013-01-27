(ns misaki.tester
  "Compiler Testing Utilities"
  (:use
    [misaki.core      :only [update-config call-compile call-index-compile]]
    [misaki.config    :only [*base-dir* *config* make-basic-config-map with-config]]
    [misaki.util.file :only [normalize-path path]]
    [clojure.test     :only [deftest]])
  (:require
    [clojure.string  :as str]
    [clojure.java.io :as io]))

;; default base-dir for tester is "test/"
(def _test-base-dir_ (atom "test/"))

; =set-base-dir!
(defn set-base-dir!
  "Set base directory for testing.
  Default base directory is \"test/\"."
  [base-dir]
  (reset! _test-base-dir_ (normalize-path base-dir)))

; =with-test-base-dir
(defmacro with-test-base-dir
  "Bind test base directory to `*base-dir*`."
  [& body]
  `(binding [*base-dir* @_test-base-dir_]
     ~@body))

; =get-base-config
(defn get-base-config
  "Get basic config map. Basically you should use `get-config`."
  []
  (with-test-base-dir
    (make-basic-config-map)))

; =get-config
(defn get-config
  "Get compiler config map.

  If config has two or more maps and `:merge? true` option is specified,
  this function returns merged config map.

  ex) (get-config)
      (get-config :merge? true)
  "
  [& {:keys [merge?] :or {merge? false}}]
  (with-test-base-dir
    (let [config (update-config)]
      (if (and merge? (sequential? config))
        ; ["compiler1" "compiler2"]
        ; => (merge [compiler2 compiler1])
        (apply merge (reverse config))
        config))))

; =test-compile
(defn test-compile
  "Run test compile."
  [file]
  (with-test-base-dir
    (call-compile file)))

; =test-index-compile
(defn test-index-compile
  ([file] (test-index-compile {} file))
  ([optional-config file]
   (with-test-base-dir
     (call-index-compile optional-config file))))

; =deftest*
(defmacro deftest*
  "Define test macro for using test base directory and config."
  [name & body]
  `(deftest ~name
     (with-test-base-dir
       (with-config
         ~@body))))

; =bind-config
(defmacro bind-config
  "Binding config value.

      (bind-config [:post-dir \"foo/bar\"]
        (println (:post-dir *config*)))
  "
  [bind-vec & body]
  `(binding [*config* (assoc *config* ~@bind-vec)]
     ~@body))

; =template-file
(defn template-file
  "Return java.io.File in template directory."
  [filename]
  (io/file (path (:template-dir *config*) filename)))

; =public-file
(defn public-file
  "Return java.io.File in pulbic directory."
  [filename]
  (io/file (path (:public-dir *config*) filename)))
