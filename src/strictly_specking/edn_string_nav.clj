(ns strictly-specking.edn-string-nav
  (:require
   [clojure.java.io :as io]
   [clojure.zip :as zip]
   [clojure.string :as string]
   [net.cgrand.sjacket :as sj]
   [net.cgrand.sjacket.parser :as p]))

;; TODO make this work for paths with sets on them

;; fun ... implement datastructure interfaces

(defn- at-newline? [loc]
  (= :newline (:tag (zip/node loc))))

#_(def line-number (partial #'line-number-of z))

(defn node-test [tag n]
  (and (map? n)
       (= (:tag n) tag)))

(def root-node? (partial node-test :net.cgrand.sjacket.parser/root))
(def keyword-node? (partial node-test :keyword))
(def map-node?     (partial node-test :map))
(def set-node?     (partial node-test :set))
(def list-node?    (partial node-test :list))
(def vector-node?  (partial node-test :vector))
(def coll-node?    (some-fn map-node? list-node? vector-node?))
(def name-node?    (partial node-test :name))
(def symbol-node?  (partial node-test :symbol))

(defn is-keyword? [k n]
  (and
   (keyword? k)
   (keyword-node? n)
   (= (get-in n [:content 1 :content])
      [(name k)])))

(defn is-project? [n]
  (and (name-node? n)
       (= (:content n)
          ["defproject"])))

(defn node [loc]
  (and loc
       (zip/node loc)))

#_(is-keyword? :description
             {:tag :keyword, :content [":" {:tag :name, :content ["description"]}]})

(defn- insignificant? [loc]
  (when-let [{:keys [tag]} (zip/node loc)]
    (p/space-nodes tag)))

(defn direction [zip-dir-fn loc]
  (->> loc
       (iterate zip-dir-fn)
       (take-while (comp not nil?))))

(defn direction-find [zip-dir-fn pred loc]
  (->> loc
       (direction zip-dir-fn)
       (filter pred)
       first))

(defn ->root-loc [loc]
  (direction-find zip/up #(root-node? (zip/node %)) loc))

(defn- find-first [pred loc]
  (->> loc
       (iterate zip/next)
       (take-while (comp not zip/end?))
       (filter (comp pred zip/node))
       first))

(defn find-project [loc]
  (->> loc
       (find-first is-project?)
       zip/up))

(defn lazy-right-list [loc]
  (->> loc
       (iterate zip/right)
       (take-while (comp not nil?))
       (remove insignificant?)))

(defn find-key-in-structure [k loc]
  (->> loc
       lazy-right-list
       (drop 1)           
       (partition 2)
       (map first)
       (filter (comp (partial is-keyword? k) zip/node))
       first))

(defn- find-key-in-project [k loc]
  (->> loc
       find-project
       (find-key-in-structure k)))

(defn find-key-in-map [k loc]
  (assert (map-node? (zip/node loc)) "Must be a map node.")
  (->> loc
       zip/down
       (find-key-in-structure k)))

(defn find-key-in-seq [k loc]
  (assert (integer? k) "Key to seq must be integer.")
  (->> loc
       zip/down
       lazy-right-list
       (drop 1)
       (drop k)
       first))

(defn project-node? [n]
      (and (symbol-node? n)
           (->> n :content first is-project?)))

(defn- next-value [loc]
  (->> loc
       lazy-right-list
       (drop 1)
       first))

(defn find-key-in-node [k loc]
  (when-let [n (zip/node loc)]
    (cond
      (project-node? n) (find-key-in-project k loc)
      (map-node? n)     (find-key-in-map k loc)
      (vector-node? n)  (find-key-in-seq k loc)
      (list-node? n)    (find-key-in-seq k loc)
      ;; TODO sets
      :else nil)))

(defn find-key-value-in-node [k loc]
  (when-let [res (find-key-in-node k loc)]
    (let [n (zip/node loc)]
      (cond
        (project-node? n) (next-value res)
        (map-node? n)     (next-value res)
        :else res))))

(defn line-number [l]
  (let [root-zip (->root-loc l)
        node (zip/node l)]
    (->> root-zip
         (direction zip/next)
         (take-while #(not= l %))
         (filter at-newline?)
         count
         inc)))

(defn get-value-at-path
  "given a path into a structure return its location"
  [path loc]
  (reduce #(find-key-value-in-node %2 %1) loc (butlast path)))

(defn get-loc-at-path
  "given a path into a structure return its location"
  [path loc]
  (when-let [value-above (get-value-at-path path loc)]
    (find-key-in-node (last path) value-above)))

(defn file-to-parsed-zipper [file]
  (when (.exists (io/file file))
    (-> (io/file file)
        slurp
        p/parser
        zip/xml-zip)))

(defn file-to-initial-position [file]
  (when-let [loc (file-to-parsed-zipper file)]
    (if-let [proj (find-project loc)]
      proj
      (find-first coll-node? loc))))

(defn- sjacket->clj [value]
  (try
    (if-not (#{:comment :whitespace :newline} (:tag value))
      (-> value sj/str-pt read-string))
    (catch Throwable e
      nil)))

#_(->> (file-to-initial-position "project.clj")
       (->root-loc))

;; *** TODO add end line and end column
(defn get-path-in-clj-file
  "Given the name of a file that holds a collection of EDN data or
   a project.clj file. This function will traverse the given path and
   return a map of the following info:

     :line   - the line number of the found item
     :column - the column number of the found item
     :value  - the value of the found item
     :path   - the path that was supplied as an argument
     :loc    - the zipper at this point

   There is some ambiguity around whether this represents a key and a
   value or just a value at a point.  If the last key is a member of a
   map, the value returned will be the value at the key position.
   Otherwise, the value at the position itself will be returned.
"

  [path file]
  (when-let [loc (file-to-initial-position file)]
    (when-let [point-loc (get-loc-at-path path loc)]
      {:line (line-number point-loc)
       :column (sj/column point-loc)
       :file file
       :value  (or (sjacket->clj (-> point-loc next-value zip/node))
                   (sjacket->clj (zip/node point-loc)))
       :path path
       :loc point-loc
       })))

#_(def x (:loc (get-path-in-clj-file [0 :asdfasdf 0] "tester.edn")))


#_(defn line-number [l]
  (let [root-zip (->root-loc l)
        node (zip/node l)]
    (->> root-zip
         (direction zip/next)
         (take-while #(not= l %))
         (filter at-newline?)
         count
         inc)))

#_(line-number x)

#_(zip/node x)
#_(->> (->root-loc x)
     (direction zip/next)
     (take
      40)
     last)


#_ (get-path-in-clj-file [:cljsbuild :builds 0] "project.clj")
