(ns strictly-specking.cljs-options-schema
  (:require
   [clojure.spec :as s]
   [strictly-specking.core :refer [strict-keys def-key-doc]]))

(defmacro def-key [k spec doc]
  `(do
     (s/def ~k ~spec)
     (def-key-doc ~k ~doc)))

;; * Specification for ClojureScript
;; ** Top level util specs

(s/def ::string-or-symbol (s/or :string string? :symbol symbol?))
(s/def ::string-or-named  (s/or :string string? :symbol symbol? :keyword keyword?))

;; ** CLJS Compiler Options
;; *** Commonly used Compiler Options

(def-key ::output-to string?
  "After your ClojureScript has been compiled to JavaScript, this
specifies the name of the JavaScript output file.  The contents of
this file will differ based on the :optimizations setting.

If :optimizations is set to :none then this file will merely contain
the code needed to load Google Closure the and rest of the compiled
namespaces (which are separate files).

If :optimizations is set to :simple, :whitespace, or :advanced this
output file will contain all the compiled code.

  :output-to \"resources/public/js/main.js\"")

(def-key ::output-dir string?
  "Sets the output directory for output files generated during
compilation.

Defaults to  \"out\".

  :output-dir \"resources/public/js/out\"")

(def-key ::optimizations #{:none :whitespace :simple :advanced}
"The optimization level. May be :none, :whitespace, :simple, or
:advanced. Only :none and :simple are supported for bootstrapped
ClojureScript.

  :none is the recommended setting for development
  
  :advanced is the recommended setting for production, unless
        something prevents it (incompatible external library, bug,
        etc.).

For a detailed explanation of the different optimization modes see
https://developers.google.com/closure/compiler/docs/compilation_levels

When the :main option is not used, :none requires manual code loading
and hence a separate HTML from the other options.

Defaults to :none. Note: lein cljsbuild 1.0.5 will supply :whitespace.

  :optimizations :none")

(def-key ::main                      ::string-or-symbol
"Specifies an entry point namespace. When combined with optimization
level :none, :main will cause the compiler to emit a single JavaScript
file that will import goog/base.js, the JavaScript file for the
namespace, and emit the required goog.require statement. This permits
leaving HTML markup identical between dev and production.

Also see :asset-path.

  :main \"example.core\"")

(def-key ::asset-path                string?
  "When using :main it is often necessary to control where the entry
point script attempts to load scripts from due to the configuration of
the web server. :asset-path is a relative URL path not a file system
path. For example, if your output directory is :ouput-dir
\"resources/public/js/compiled/out\" but your webserver is serving files
from \"resources/public\" then you want the entry point script to load
scripts from \"js/compiled/out\".

  :asset-path \"js/compiled/out\"")

(def-key ::source-map                (s/or :bool boolean? :string string?)
  "See https://github.com/clojure/clojurescript/wiki/Source-maps. Under
optimizations :none the valid values are true and false, with the
default being true. Under all other optimization settings must specify
a path to where the source map will be written.

Under :simple, :whitespace, or :advanced
  :source-map \"path/to/source/map.js.map\"")


(s/def ::preloads                  (s/+ symbol?)
  "Developing ClojureScript commonly requires development time only
side effects such as enabling printing, logging, spec instrumentation,
and connecting REPLs. :preloads permits loading such side effect
boilerplate right after cljs.core. For example you can make a
development namespace for enabling printing in browsers:

  (ns foo.dev)

  (enable-console-print!)

Now you can configure your development build to load this side effect
prior to your main namespace with the following compiler options:

  {:preloads [foo.dev]
   :main \"foo.core\"" "
   :output-dir \"out\"}

The :preloads config value must be a sequence of symbols that map to
existing namespaces discoverable on the classpath.")

(def-key ::verbose                   boolean?
"Emit details and measurements from compiler activity.

  :verbose true")

(s/def ::pretty-print              boolean?
  "Determines whether the JavaScript output will be tabulated in a
human-readable manner. Defaults to true.

  :pretty-print false")

(s/def ::target                    #{:nodejs}
"If targeting nodejs add this line. Takes no other options at the
moment. The default (no :target specified) implies browsers are being
targeted. Have a look here for more information on how to run your
code in nodejs.

  :target :nodejs")

(s/def ::foreign-libs
  (strict-keys
   :req-un [::file
            ::provides]
   :opt-un [::file-min
            ::requires
            ::module-type
            ::preprocess])
  "Adds dependencies on foreign libraries. Be sure that the url returns a
HTTP Code 200

Defaults to the empty vector []

  :foreign-libs [{ :file \"http://example.com/remote.js\"
                   :provides  [\"my.example\"]}]

Each element in the :foreign-libs vector should be a map, where the
keys have these semantics:

  :file Indicates the URL to the library

  :file-min (Optional) Indicates the URL to the minified variant of
            the library.
  
  :provides A synthetic namespace that is associated with the library.
            This is typically a vector with a single string, but it
            has the capability of specifying multiple namespaces
            (typically used only by Google Closure libraries).
  
  :requires (Optional) A vector explicitly identifying dependencies
            (:provides values from other foreign libs); used to form a
            topological sort honoring dependencies.

  :module-type (Optional) indicates that the foreign lib uses a given
               module system. Can be one of :commonjs, :amd, :es6.
               Note that if supplied, :requires is not used (as it is
               implicitly determined).
  
  :preprocess (Optional) Used to preprocess / transform code in other
              dialects (JSX, etc.). A defmethod for
              cljs.clojure/js-transforms must be provided that matches
              the supplied value in order to effect the desired code
              transformation.")

(s/def ::file        string?)
(s/def ::provides    (s/+ string?))
(s/def ::file-min    string?)
(s/def ::requires    (s/+ string?))
(s/def ::module-type #{:commonjs :amd :es6})
(s/def ::preprocess  ::string-or-named)

(s/def ::externs                    (s/+ string?)
"Configure externs files for external libraries.

For this option, and those below, you can find a very good explanation at:
http://lukevanderhart.com/2011/09/30/using-javascript-and-clojurescript.html

Defaults to the empty vector [].

  :externs [\"jquery-externs.js\"]")

(s/def ::modules
  (s/map-of
   keyword?
   (strict-keys
    :req-un [:cljs.options-schema.modules/output-dir
             ::entries]
    :opt-un [::depends-on]))
"A new option for emitting Google Closure Modules. Closure Modules
supports splitting up an optimized build into N different modules. If
:modules is supplied it replaces the single :output-to. A module needs
a name, an individual :output-to file path, :entries a set of
namespaces, and :depends-on a set of modules on which the module
depends. Modules are only supported with :simple and :advanced
optimizations. An example follows:

  {:optimizations :advanced
   :source-map true
   :output-dir \"resources/public/js\"
   :modules {
     :common 
       {:output-to \"resources/public/js/common.js\"  
        :entries #{\"com.foo.common\"}}
     :landing 
       {:output-to \"resources/public/js/landing.js\" 
        :entries #{\"com.foo.landing\"}
        :depends-on #{:common}}
     :editor 
       {:output-to \"resources/public/js/editor.js\"
        :entries #{\"com.foo.editor\"}
        :depends-on #{:common}}}}


Any namespaces not in an :entries set will be moved into the default
module :cljs-base. However thanks to cross module code motion, Google
Closure can move functions and methods into the modules where they are
actually used. This process is somewhat conservative so if you know
that you want to keep some code together do this via :entries.

The :cljs-base module defaults to being written out to :output-dir
with the name \"cljs_base.js\". This may be overridden by specifying a
:cljs-base module describing only :output-to.

Take careful note that a namespace may only appear once across all
module :entries.

:modules fully supports :foreign-libs. :foreign-libs are always put
into dependency order before any Google Closure compiled source.

Source maps are fully supported, an individual one will be created for
each module. Just supply :source-map true (see example) as there is no
single source map to name.")

;; TODO name collision don't want docs
;; think about ramifications
(s/def :cljs.options-schema.modules/output-dir    string?) 
(s/def ::entries       (s/+ string?))
(s/def ::depends-on    (s/+ ::string-or-named))

(s/def ::source-map-path            string?
"Set the path to source files references in source maps to avoid
further web server configuration.

  :source-map-path \"public/js\"")

(s/def ::source-map-timestamp       boolean? 
"Add cache busting timestamps to source map urls. This is helpful for
keeping source maps up to date when live reloading code.

  :source-map-timestamp true")

(s/def ::cache-analysis             boolean?
"Experimental. Cache compiler analysis to disk. This enables faster
cold build and REPL start up times.

For REPLs, defaults to true. Otherwise, defaults to true if and only
if :optimizations is :none.

  :cache-analysis true")

(s/def ::recompile-dependents       boolean?
"For correctness the ClojureScript compiler now always recompiles
dependent namespaces when a parent namespace changes. This prevents
corrupted builds and swallowed warnings. However this can impact
compile times depending on the structure of the application. This
option defaults to true.

  :recompile-dependents false")

(s/def ::static-fns                 boolean?
"Employs static dispatch to specific function arities in emitted
JavaScript, as opposed to making use of the call construct. Defaults
to false except under advanced optimizations. Useful to have set to
false at REPL development to facilitate function redefinition, and
useful to set to true for release for performance.

This setting does not apply to the standard library, which is always
compiled with :static-fns implicitly set to true.

  :static-fns true")

;; (s/def ::warnings                   (ref-schema 'CompilerWarnings))

(s/def ::elide-asserts              boolean?
  "This flag will cause all (assert x) calls to be removed during
compilation, including implicit asserts associated with :pre and :post
conditions. Useful for production. Default is always false even in
advanced compilation. Does NOT specify goog.asserts.ENABLE_ASSERTS,
which is different and used by the Closure library.

Note that it is currently not possible to dynamically set *assert* to
false at runtime; this compiler flag must explicitly be used to effect
the elision.

  :elide-asserts true")

(s/def ::pseudo-names               boolean?
  "With :advanced mode optimizations, determines whether readable names
are emitted. This can be useful when debugging issues in the optimized
JavaScript and can aid in finding missing externs. Defaults to false.

  :pseudo-names true")

(s/def ::print-input-delimiter      boolean?
 "Determines whether comments will be output in the JavaScript that can
be used to determine the original source of the compiled code.

Defaults to false.

  :print-input-delimiter false")

(s/def ::output-wrapper             boolean?
  "Wrap the JavaScript output in (function(){...};)() to avoid clobbering
globals. Defaults to false.

  :output-wrapper false")

(s/def ::libs                       (s/+ string?)
  "Adds dependencies on external js libraries, i.e. Google
Closure-compatible javascript files with correct goog.provides() and
goog.requires() calls. Note that files in these directories will be
watched and a rebuild will occur if they are modified.

Paths or filenames can be given. Relative paths are relative to the
current working directory (usually project root).

Defaults to the empty vector []

  :libs [\"closure/library/third_party/closure\"
         \"src/js\"
         \"src/org/example/example.js\"]")

(s/def ::preamble                   (s/+ string?)
"Prepends the contents of the given files to each output file. Only
valid with optimizations other than :none.

Defaults to the empty vector []

  :preamble [\"license.js\"]")

(s/def ::hashbang                   boolean?
"When using :target :nodejs the compiler will emit a shebang as the
first line of the compiled source, making it executable. When your
intention is to build a node.js module, instead of executable, use
this option to remove the shebang.

  :hashbang false")

(s/def ::compiler-stats             boolean?
"Report basic timing measurements on compiler activity.

Defaults to false.

  :compiler-stats true")

(s/def ::language-in                #{:ecmascript3 :ecmascript5 :ecmascript5-strict}
"Configure the input and output languages for the closure library. May
be :ecmascript3, ecmascript5, ecmascript5-strict, :ecmascript6-typed,
:ecmascript6-strict, :ecmascript6 or :no-transpile.

Defaults to :ecmascript3

  :language-in  :ecmascript3")

(s/def ::language-out               #{:ecmascript3 :ecmascript5 :ecmascript5-strict}
"Configure the input and output languages for the closure library. May
be :ecmascript3, ecmascript5, ecmascript5-strict, :ecmascript6-typed,
:ecmascript6-strict, :ecmascript6 or :no-transpile.

Defaults to :ecmascript3

  :language-out  :ecmascript3")

(s/def ::closure-defines
  (s/map-of
   ::string-or-symbol
   (s/or :number number? :string string? :bool   boolean?))
  "Set the values of Closure libraries' variables annotated with @define
or with the cljs.core/goog-define helper macro. A common usage is
setting goog.DEBUG to false:

  :closure-defines {\"goog.DEBUG\" false}

or

  :closure-defines {'goog.DEBUG false}

Note when using Lein the quote is unnecessary due to implicit quoting.

For :optimization :none, a :main option must be specified for defines
to work, and only goog-define defines are affected. :closure-defines
currently does not have any effect with :optimization :whitespace.")

(s/def ::closure-extra-annotations  (s/+ string?)
"Define extra JSDoc annotations that a closure library might use so
that they don't trigger compiler warnings.

  :closure-extra-annotations #{\"api\"}")

(s/def ::anon-fn-naming-policy      #{:off :unmapped :mapped}
"Strategies for how the Google Closure compiler does naming of
anonymous functions that occur as r-values in assignments and variable
declarations. Defaults to :off.

  :anon-fn-naming-policy :unmapped

The following values are supported:

  :off Don't give anonymous functions names.
  
  :unmapped Generates names that are based on the left-hand side of
            the assignment. Runs after variable and property renaming,
            so that the generated names will be short and obfuscated.
  
  :mapped Generates short unique names and provides a mapping from
          them back to a more meaningful name that's based on the
          left-hand side of the assignment.")

(s/def ::optimize-constants         boolean?
"When set to true, constants, such as keywords and symbols, will only
be created once and will be written to a separate file called
constants_table.js. The compiler will emit a reference to the constant
as defined in the constants table instead of creating a new object for
it. This option is mainly intended to be used for a release build
since it can increase performance due to decreased allocation.
Defaults to true under :advanced optimizations otherwise to false.

  :optimize-constants true")

(s/def ::parallel-build             boolean?
  "When set to true, compile source in parallel, utilizing multiple cores.

:parallel-build true")

(s/def ::devcards                   boolean?
"Whether to include devcard 'defcard' definitions in the output of the compile.")

(s/def ::dump-core                  boolean?)
(s/def ::emit-constants             boolean?)
(s/def ::warning-handlers           (s/+ ::s/any)) ;; symbol, string, or fn?
(s/def ::source-map-inline          boolean?)
(s/def ::ups-libs                   (s/+ string?))
(s/def ::ups-externs                (s/+ string?))
(s/def ::ups-foreign-libs           (s/+ ::foreign-libs))
(s/def ::closure-output-charset     string?)
(s/def ::external-config            (s/map-of keyword? map?))

;; ** ClojureScript Compiler Warnings

(s/def ::warnings
  (strict-keys
   :opt-un
   [::undeclared-ns-form
    ::protocol-deprecated
    ::undeclared-protocol-symbol
    ::fn-var
    ::invalid-arithmetic
    ::preamble-missing
    ::undeclared-var
    ::protocol-invalid-method
    ::variadic-max-arity
    ::multiple-variadic-overloads
    ::fn-deprecated
    ::redef
    ::fn-arity
    ::invalid-protocol-symbol
    ::dynamic
    ::undeclared-ns
    ::overload-arity
    ::extending-base-js-type
    ::single-segment-namespace
    ::protocol-duped-method
    ::protocol-multiple-impls
    ::invoke-ctor])
  "This flag will turn on/off compiler warnings for references to
undeclared vars, wrong function call arities, etc. Can be a boolean
for enabling/disabling common warnings, or a map of specific warning
keys with associated booleans. Defaults to true.

  :warnings true
  
;; OR

  :warnings {:fn-deprecated false} ;; suppress this warning

The following warnings are supported:

  :preamble-missing, missing preamble
  :undeclared-var, undeclared var
  :undeclared-ns, var references non-existent namespace
  :undeclared-ns-form, namespace reference in ns form that does not exist
  :redef, var redefinition
  :dynamic, dynamic binding of non-dynamic var
  :fn-var, var previously bound to fn changed to different type
  :fn-arity, invalid invoke arity
  :fn-deprecated, deprecated function usage
  :protocol-deprecated, deprecated protocol usage
  :undeclared-protocol-symbol, undeclared protocol referred
  :invalid-protocol-symbol, invalid protocol symbol
  :multiple-variadic-overloads, multiple variadic arities
  :variadic-max-arity, arity greater than variadic arity
  :overload-arity, duplicate arities
  :extending-base-js-type, JavaScript base type extension
  :invoke-ctor, type constructor invoked as function
  :invalid-arithmetic, invalid arithmetic
  :protocol-invalid-method, protocol method does not match declaration
  :protocol-duped-method, duplicate protocol method implementation
  :protocol-multiple-impls, protocol implemented multiple times
  :single-segment-namespace, single segment namespace")

(s/def ::undeclared-ns-form          boolean?)
(s/def ::protocol-deprecated         boolean?)
(s/def ::undeclared-protocol-symbol  boolean?)
(s/def ::fn-var                      boolean?)
(s/def ::invalid-arithmetic          boolean?)
(s/def ::preamble-missing            boolean?)
(s/def ::undeclared-var              boolean?)
(s/def ::protocol-invalid-method     boolean?)
(s/def ::variadic-max-arity          boolean?)
(s/def ::multiple-variadic-overloads boolean?)
(s/def ::fn-deprecated               boolean?)
(s/def ::redef                       boolean?)
(s/def ::fn-arity                    boolean?)
(s/def ::invalid-protocol-symbol     boolean?)
(s/def ::dynamic                     boolean?)
(s/def ::undeclared-ns               boolean?)
(s/def ::overload-arity              boolean?)
(s/def ::extending-base-js-type      boolean?)
(s/def ::single-segment-namespace    boolean?)
(s/def ::protocol-duped-method       boolean?)
(s/def ::protocol-multiple-impls     boolean?)
(s/def ::invoke-ctor                 boolean?)

;; ** Closure Compiler Warnings

(s/def ::closure-warnings
  (strict-keys
   :opt-un
   [::access-controls
    ::ambiguous-function-decl
    ::debugger-statement-present
    ::check-regexp
    ::check-types
    ::check-useless-code
    ::check-variables
    ::const
    ::constant-property
    ::deprecated
    ::duplicate-message
    ::es5-strict
    ::externs-validation
    ::fileoverview-jsdoc
    ::global-this
    ::internet-explorer-checks
    ::invalid-casts
    ::missing-properties
    ::non-standard-jsdoc
    ::strict-module-dep-check
    ::tweaks
    ::undefined-names
    ::undefined-variables
    ::unknown-defines
    ::visiblity])
  "Configure warnings generated by the Closure compiler. A map from
Closure warning to configuration value, only :error, :warning and :off
are supported.

  :closure-warnings {:externs-validation :off}

The following Closure warning options are exposed to ClojureScript:

  :access-controls
  :ambiguous-function-decl
  :debugger-statement-present
  :check-regexp
  :check-types
  :check-useless-code
  :check-variables
  :const
  :constant-property
  :deprecated
  :duplicate-message
  :es5-strict
  :externs-validation
  :fileoverview-jsdoc
  :global-this
  :internet-explorer-checks
  :invalid-casts
  :missing-properties
  :non-standard-jsdoc
  :strict-module-dep-check
  :tweaks
  :undefined-names
  :undefined-variables
  :unknown-defines
  :visiblity
  
See the Closure Compiler Warning wiki for detailed descriptions.")

(s/def ::warning-value #{:error :warning :off})

(s/def ::access-controls            ::warning-value)
(s/def ::ambiguous-function-decl    ::warning-value)
(s/def ::debugger-statement-present ::warning-value)
(s/def ::check-regexp               ::warning-value)
(s/def ::check-types                ::warning-value)
(s/def ::check-useless-code         ::warning-value)
(s/def ::check-variables            ::warning-value)
(s/def ::const                      ::warning-value)
(s/def ::constant-property          ::warning-value)
(s/def ::deprecated                 ::warning-value)
(s/def ::duplicate-message          ::warning-value)
(s/def ::es5-strict                 ::warning-value)
(s/def ::externs-validation         ::warning-value)
(s/def ::fileoverview-jsdoc         ::warning-value)
(s/def ::global-this                ::warning-value)
(s/def ::internet-explorer-checks   ::warning-value)
(s/def ::invalid-casts              ::warning-value)
(s/def ::missing-properties         ::warning-value)
(s/def ::non-standard-jsdoc         ::warning-value)
(s/def ::strict-module-dep-check    ::warning-value)
(s/def ::tweaks                     ::warning-value)
(s/def ::undefined-names            ::warning-value)
(s/def ::undefined-variables        ::warning-value)
(s/def ::unknown-defines            ::warning-value)
(s/def ::visiblity                  ::warning-value)

(s/def ::compiler-options
  (strict-keys
   :opt-un
   [::main
    ::preloads
    ::asset-path
    ::output-to
    ::output-dir
    ::optimizations
    ::source-map
    ::verbose
    ::pretty-print
    ::target
    ::foreign-libs
    ::externs
    ::modules
    ::source-map-path
    ::source-map-timestamp
    ::cache-analysis
    ::recompile-dependents
    ::static-fns
    ::elide-asserts
    ::pseudo-names
    ::print-input-delimiter
    ::output-wrapper
    ::libs
    ::preamble
    ::hashbang
    ::compiler-stats
    ::language-in
    ::language-out
    ::closure-defines
    ::closure-extra-annotations
    ::anon-fn-naming-policy
    ::optimize-constants
    ::parallel-build
    ::devcards
    ::dump-core
    ::emit-constants
    ::warning-handlers
    ::source-map-inline
    ::ups-libs
    ::ups-externs
    ::ups-foreign-libs
    ::closure-output-charset
    ::external-config]))


;; ** Figwheel Client Options

(s/def ::build-id             string?)
(s/def ::websocket-host       (s/or :string string?
                                                           :host-option
                                                           #{:js-client-host :server-ip :server-hostname}))
(s/def ::websocket-url        string?)
(s/def ::on-jsload            ::string-or-named)
(s/def ::before-jsload        ::string-or-named)
(s/def ::on-cssload           ::string-or-named)
(s/def ::on-message           ::string-or-named)
(s/def ::on-compile-fail      ::string-or-named)
(s/def ::on-compile-warning   ::string-or-named)
(s/def ::reload-dependents    boolean?)
(s/def ::debug                boolean?)
(s/def ::autoload             boolean?)
(s/def ::heads-up-display     boolean?)
(s/def ::load-warninged-code  boolean?)
(s/def ::retry-count          integer?)
(s/def ::devcards             boolean?)
(s/def ::eval-fn              ::string-or-named)
(s/def ::open-urls            (s/+ string?))

;; ** Figwheel Build Config

(s/def ::id               ::string-or-named)
(s/def ::source-paths     (s/+ string?))
(s/def ::notify-command   (s/+ string?))
(s/def ::jar              boolean?)
(s/def ::incremental      boolean?)
(s/def ::assert           boolean?)
(s/def ::warning-handlers (s/+ ::s/any))

(s/def ::figwheel
  (s/or
   :bool boolean?
   :figwheel-client-options
   (strict-keys
    :opt-un
    [::build-id
     ::websocket-host
     ::websocket-url
     ::on-jsload
     ::before-jsload
     ::on-cssload
     ::on-message
     ::on-compile-fail
     ::on-compile-warning
     ::reload-dependents
     ::debug
     ::autoload
     ::heads-up-display
     ::load-warninged-code
     ::retry-count
     ::devcards
     ::eval-fn
     ::open-urls])))

(s/def ::compiler
  (strict-keys
   :opt-un
   [::main
    ::preloads
    ::asset-path
    ::output-to
    ::output-dir
    ::optimizations
    ::source-map
    ::verbose
    ::pretty-print
    ::target
    ::foreign-libs
    ::externs
    ::modules
    ::source-map-path
    ::source-map-timestamp
    ::cache-analysis
    ::recompile-dependents
    ::static-fns
    ::elide-asserts
    ::pseudo-names
    ::print-input-delimiter
    ::output-wrapper
    ::libs
    ::preamble
    ::hashbang
    ::compiler-stats
    ::language-in
    ::language-out
    ::closure-defines
    ::closure-extra-annotations
    ::anon-fn-naming-policy
    ::optimize-constants
    ::parallel-build
    ::devcards
    ::dump-core
    ::emit-constants
    ::warning-handlers
    ::source-map-inline
    ::ups-libs
    ::ups-externs
    ::ups-foreign-libs
    ::closure-output-charset
    ::external-config]))

(s/def ::options
  (strict-keys
   :opt-un
   [::id
    ::notify-command
    ::jar
    ::incremental
    ::assert
    ::warning-handlers
    ::figwheel]
   :req-un
   [::source-paths
    ::compiler]))

(s/def ::compiler-require-id
  (s/and
   ::options
   (s/keys
    :req-un [::id])))

(s/def ::http-server-root  string?)
(s/def ::server-port       integer?)
(s/def ::server-ip         string?)
(s/def ::css-dirs          (s/+ string?))
(s/def ::ring-handler      ::string-or-named)
(s/def ::builds-to-start   (s/+ ::string-or-named))
(s/def ::server-logfile    string?)
(s/def ::open-file-command string?)
(s/def ::repl              boolean?)
(s/def ::nrepl-port        integer?)
(s/def ::nrepl-host        string?)
(s/def ::nrepl-middleware  (s/+ ::string-or-named)) ;; allow inlined
(s/def ::validate-config   boolean?)
(s/def ::load-all-builds   boolean?)
(s/def ::ansi-color-output boolean?)

(s/def ::builds
  (s/or
   :builds-vector (s/+ ::compiler-require-id)
   :builds-map    (s/map-of ::string-or-named ::options)))

(s/def ::hawk-options
  (s/map-of #{:watcher} #{:barbary :java :polling}))

(s/def ::clj  boolean?)
(s/def ::cljs boolean?)

(s/def ::reload-clj-files
  (s/or
   :bool boolean?
   :suffix-map
   (strict-keys
    :opt-un
    [::clj
     ::cljs])))

;; ** Lein Cljsbuild Options

#_(s/def ::builds                (ref-schema 'CljsBuilds))
(s/def ::repl-listen-port      integer?)
(s/def ::repl-launch-commands  (map-of
                                                 ::string-or-named
                                                 (s/+ ::string-or-named)))
(s/def ::test-commands         (map-of
                                                 ::string-or-named
                                                 (s/+ ::string-or-named)))
(s/def ::crossovers            (s/+ ::s/any))
(s/def ::crossover-path        (s/+ ::s/any))
(s/def ::crossover-jar         boolean?)

(s/def ::cljsbuilds
  (strict-keys
   :opt-un [::builds
            ::repl-listen-port
            ::repl-launch-commands
            ::test-commands
            ::crossovers
            ::crossover-path
            ::crossover-jar]))


;; ** Different Top Level Schemas depending on context

