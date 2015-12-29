(ns schema.core-test
  #+clj (:use clojure.test schema.test-macros)
  #+cljs
  (:use-macros
   [cljs-test.macros :only [is is= deftest]]
   [schema.test-macros :only [valid! invalid!]])
  #+cljs
  (:require-macros
   [schema.macros :as sm])
  (:require
   #+clj potemkin
   [schema.core :as s]
   #+clj [schema.macros :as sm]))

;; (sm/defrecord Explainer
;;     [^long foo ^String bar]
;;   {(s/optional-key :baz) clojure.lang.Keyword})

;; (deftest explain-test
;;   (is (= (s/explain {(s/required-key 'x) long
;;                      String [(s/one int "foo") (s/maybe Explainer)]})
;;          '{(required-key x) long
;;            java.lang.String [("foo" int)
;;                              &
;;                              (maybe
;;                               (schema.core_test.Explainer
;;                                {:foo long
;;                                 :bar java.lang.String
;;                                 (optional-key :baz) clojure.lang.Keyword}))]})))


;;; leaves

(deftest class-test
  (valid! s/Str "foo")
  (invalid! s/Str :foo))

(deftest fn-test
  (valid! odd? 1)
  (invalid! odd? 2)
  (invalid! #(/ % 0) 2)
  (invalid! odd? :foo))

#+clj
(do
  (deftest primitive-test
    (valid! float (float 1.0))
    (invalid! float 1.0)
    (valid! double 1.0)
    (invalid! double (float 1.0))
    (valid! boolean true)
    (invalid! boolean 1)
    (doseq [f [byte char short int]]
      (valid! f (f 1))
      (invalid! f 1))
    (valid! long 1)
    (invalid! long (byte 1)))

  (deftest array-test
    (valid! (Class/forName"[Ljava.lang.String;") (into-array String ["a"]))
    (invalid! (Class/forName "[Ljava.lang.Long;") (into-array String ["a"]))
    (valid! (Class/forName "[Ljava.lang.Double;") (into-array Double [1.0]))
    (valid! (Class/forName "[D") (double-array [1.0]))))

(deftest eq-test
  (let [schema (s/eq 10)]
    (valid! schema 10)
    (invalid! schema 9)))

(deftest enum-test
  (let [schema (s/enum :a :b 1)]
    (valid! schema :a)
    (valid! schema 1)
    (invalid! schema :c)
    (invalid! schema 2)))

(defprotocol ATestProtocol)
(def +protocol-schema+ ATestProtocol) ;; make sure we don't fuck it up by capturing the earlier value.
(defrecord DirectTestProtocolSatisfier [] ATestProtocol)
(defrecord IndirectTestProtocolSatisfier []) (extend-type IndirectTestProtocolSatisfier ATestProtocol)
(defrecord NonTestProtocolSatisfier [])

(deftest protocol-test
  (let [s (s/protocol ATestProtocol)]
    (valid! s (DirectTestProtocolSatisfier.))
    (valid! s (IndirectTestProtocolSatisfier.))
    (invalid! s (NonTestProtocolSatisfier.))
    (invalid! s nil)
    (invalid! s 117)))

;;; helpers/wrappers

(deftest anything-test
  (valid! s/Any 1)
  (valid! s/Any nil)
  (valid! s/Any #{:hi :there}))

(deftest either-test
  (let [schema (s/either
                {:num s/Int}
                {:str s/Str})]
    (valid! schema {:num 1})
    (valid! schema {:str "hello"})
    (invalid! schema {:num "bad!"})
    (invalid! schema {:str 1})))

(deftest both-test
  (let [schema (s/both
                (fn equal-keys? [m] (every? (fn [[k v]] (= k v)) m))
                {s/Key s/Key})]
    (valid! schema {})
    (valid! schema {:foo :foo :bar :bar})
    (invalid! schema {"foo" "foo"})
    (invalid! schema {:foo :bar})))

(deftest maybe-test
  (let [schema (s/maybe s/Int)]
    (is (= schema (s/? s/Int)))
    (valid! schema nil)
    (valid! schema 1)
    (invalid! schema 1.0)))

(deftest named-test
  (let [schema [(s/one s/Str "topic") (s/one (s/named s/Num "score") "asdf")]]
    (valid! schema ["foo" 1.0])
    (invalid! schema [1 2])))

(deftest conditional-test
  (let [schema (s/conditional #(= (:type %) :foo) {:type (s/eq :foo) :baz s/Num}
                              #(= (:type %) :bar) {:type (s/eq :bar) :baz s/Str})]
    (valid! schema {:type :foo :baz 10})
    (valid! schema {:type :bar :baz "10"})
    (invalid! schema {:type :foo :baz "10"})
    (invalid! schema {:type :bar :baz 10})
    (invalid! schema {:type :zzz :baz 10})))



;;; maps

(deftest simple-map-schema-test
  (let [schema {:foo s/Int
                :bar s/Num}]
    (valid! schema {:foo 1 :bar 2.0})
    (invalid! schema [[:foo 1] [:bar 2.0]])
    (invalid! schema {:foo 1 :bar 2.0 :baz 1})
    (invalid! schema {:foo 1})
    (invalid! schema {:foo 1.0 :bar 1.0})))

(deftest fancier-map-schema-test
  (let [schema {:foo s/Int
                s/Str s/Num}]
    (valid! schema {:foo 1})
    (valid! schema {:foo 1 "bar" 2.0})
    (valid! schema {:foo 1 "bar" 2.0 "baz" 10.0})
    (invalid! schema {:foo 1 :bar 2.0})
    (invalid! schema {:foo 1 :bar 2.0 "baz" 2.0})
    (invalid! schema {:foo 1 "bar" "a"})))

(deftest another-fancy-map-schema-test
  (let [schema {:foo (s/maybe s/Int)
                (s/optional-key :bar) s/Num
                :baz {:b1 odd?}}]
    (valid! schema {:foo 1 :bar 1.0 :baz {:b1 3}})
    (valid! schema {:foo 1 :baz {:b1 3}})
    (valid! schema {:foo nil :baz {:b1 3}})
    (invalid! schema {:foo 1 :bar 1.0 :baz [[:b1 3]]})
    (invalid! schema {:foo 1 :bar 1.0 :baz {:b2 3}})
    (invalid! schema {:foo 1 :bar 1.0 :baz {:b1 4}})
    (invalid! schema {:bar 1.0 :baz {:b1 3}})
    (invalid! schema {:foo 1 :bar nil :baz {:b1 3}})))

;;; sequences

(deftest simple-repeated-seq-test
  (let [schema [long]]
    (valid! schema [])
    (valid! schema [1 2 3])
    (invalid! schema {})
    (invalid! schema [1 2 1.0])))

(deftest simple-one-seq-test
  (let [schema [(s/one s/Int "int") (s/one s/Str "str")]]
    (valid! schema [1 "a"])
    (invalid! schema [1])
    (invalid! schema [1 1.0 2])
    (invalid! schema [1 1])
    (invalid! schema [1.0 1.0])))

(deftest combo-seq-test
  (let [schema [(s/one (s/maybe s/Int) "maybe-long") s/Num]]
    (valid! schema [1])
    (valid! schema [1 1.0 2.0 3.0])
    (valid! schema [nil 1.0 2.0 3.0])
    (invalid! schema [1.0 2.0 3.0])
    (invalid! schema [])))

;; TODO: most of the invalid! cases above should be replaced with
;; explicit checks on the error returned by check?
#+clj
(deftest nice-error-test
  (is (= (str (s/check
               {:a long
                :b [(s/one double "d") long]}
               {:a "test"
                :b [1 2 2]
                :c "foo"}))
         (str (array-map
               :a '(not (instance? java.lang.Long "test"))
               :b '[(not (instance? java.lang.Double 1)) nil nil]
               :c 'disallowed-key)))))

;;; sets

(deftest simple-set-test
  ;; basic set identification
  (let [schema #{s/Key}]
    (valid! schema #{:a :b :c})
    (invalid! schema [:a :b :c])
    (invalid! schema {:a :a :b :b}))

  ;; enforces matching with single simple entry
  (let [schema #{s/Int}]
    (valid! schema #{})
    (valid! schema #{1 2 3})
    (invalid! schema #{1 0.5 :a})
    (invalid! schema #{3 4 "a"}))
  ;; not allowed to have zero or multiple entries
  (is (thrown? Exception (s/check #{s/Int s/Num} #{})))


  ;; slightly more complicated elem-schema
  (let [schema #{[s/Int]}]
    (valid! schema #{})
    (valid! schema #{[2 4]})
    (invalid! schema #{2})
    (invalid! schema #{[[2 3]]})))

(deftest mixed-set-test
  (let [schema #{(s/either [s/Int] #{s/Int})}]
    (valid! schema #{})
    (valid! schema #{[3 4] [56 1] [-11 3]})
    (valid! schema #{#{3 4} #{56 1} #{-11 3}})
    (valid! schema #{[3 4] #{56 1} #{-11 3}})
    (invalid! schema #{#{[3 4]}})
    (invalid! schema #{[[3 4]]})))


;;; records

(defrecord Foo [x ^{:s s/Int} y])

(deftest record-test
  (let [schema (s/record Foo {:x s/Any (s/optional-key :y) s/Int})]
    (valid! schema (Foo. :foo 1))
    (invalid! schema {:x :foo :y 1})
    (invalid! schema (assoc (Foo. :foo 1) :bar 2))))

(deftest record-with-extra-keys test
  (let [schema (s/record Foo {:x s/Any
                              :y s/Int
                              s/Key s/Any})]
    (valid! schema (Foo. :foo 1))
    (valid! schema (assoc (Foo. :foo 1) :bar 2))
    (invalid! schema {:x :foo :y 1})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schematized defrecord

(defmacro test-normalized-meta [symbol ex-schema desired-meta]
  (let [normalized (schema.macros/normalized-metadata &env symbol ex-schema)]
    `(do (is (= '~symbol '~normalized))
         (is (= ~(select-keys desired-meta [:schema :tag])
                ~(select-keys (meta normalized) [:schema :tag]))))))

#+clj
(do
  (def ASchema [long])

  (deftest normalized-metadata-test
    (testing "empty" (test-normalized-meta 'foo nil {:schema s/Any}))
    (testing "protocol" (test-normalized-meta ^ATestProtocol foo nil {:schema (s/protocol ATestProtocol)}))
    (testing "primitive" (test-normalized-meta ^long foo nil {:tag long :schema long}))
    (testing "class" (test-normalized-meta ^String foo nil {:tag String :schema String}))
    (testing "non-tag" (test-normalized-meta ^ASchema foo nil {:schema ASchema}))
    (testing "both" (test-normalized-meta ^{:tag Object :schema String} foo nil {:tag Object :schema String}))
    (testing "explicit" (test-normalized-meta ^Object foo String {:tag Object :schema String}))))

(defmacro test-meta-extraction [meta-form arrow-form]
  (let [meta-ized (schema.macros/process-arrow-schematized-args {} arrow-form)]
    `(do (is (= '~meta-form '~meta-ized))
         (is (= ~(mapv #(select-keys (meta (schema.macros/normalized-metadata {} % nil)) [:schema :tag]) meta-form)
                ~(mapv #(select-keys (meta %) [:schema :tag]) meta-ized)))))

  (deftest extract-arrow-schematized-args-test
    (testing "empty" (test-meta-extraction [] []))
    (testing "no-tag" (test-meta-extraction [x] [x]))
    (testing "old-tags" (test-meta-extraction [^String x] [^String x]))
    (testing "new-vs-old-tag" (test-meta-extraction [^String x] [x :- String]))
    (testing "multi vars" (test-meta-extraction [x ^{:schema [String]} y z] [x y :- [String] z]))))

(potemkin/defprotocol+ PProtocol
  (do-something [this]))

;; exercies some different arities
(sm/defrecord Bar
    [^s/Int foo  ^s/Str bar]
  {(s/optional-key :baz) s/Key})

(sm/defrecord Bar2
    [^s/Int foo ^s/Str bar]
  {(s/optional-key :baz) s/Key}
  PProtocol
  (do-something [this] 2))

(sm/defrecord Bar3
    [^s/Int foo ^s/Str bar]
  PProtocol
  (do-something [this] 3))

(sm/defrecord Bar4
    [^{:s [s/Int]} foo ^{:s? {s/Str s/Str}} bar]
  PProtocol
  (do-something [this] 4))

(deftest defrecord-schema-test
  (is (= (s/class-schema Bar)
         (s/record Bar {:foo s/Int
                        :bar s/Str
                        (s/optional-key :baz) s/Key})))
  (is (Bar. 1 :foo))
  (is (= #{:foo :bar} (set (keys (map->Bar {:foo 1})))))
  (is (thrown? Exception (map->Bar {}))) ;; check for primitive long
  (valid! Bar (Bar. 1 "test"))
  (invalid! Bar (Bar. 1 :foo))
  (valid! Bar (assoc (Bar. 1 "test") :baz :foo))
  (invalid! Bar (assoc (Bar. 1 "test") :baaaz :foo))
  (invalid! Bar (assoc (Bar. 1 "test") :baz "foo"))

  (valid! Bar2 (assoc (Bar2. 1 "test") :baz :foo))
  (invalid! Bar2 (assoc (Bar2. 1 "test") :baaaaz :foo))
  (is (= 2 (do-something (Bar2. 1 "test"))))

  (valid! Bar3 (Bar3. 1 "test"))
  (invalid! Bar3 (assoc (Bar3. 1 "test") :foo :bar))
  (is (= 3 (do-something (Bar3. 1 "test"))))

  (valid! Bar4 (Bar4. [1] {"test" "test"}))
  (valid! Bar4 (Bar4. [1] nil))
  (invalid! Bar4 (Bar4. ["a"] {"test" "test"}))
  (is (= 4 (do-something (Bar4. 1 "test")))))

(sm/defrecord BarNewStyle
    [foo :- s/Int
     bar :- s/Str]
  {(s/optional-key :baz) s/Key})

(deftest defrecord-new-style-schema-test
  (is (= (s/class-schema BarNewStyle)
         (s/record BarNewStyle {:foo s/Int
                                :bar s/Str
                                (s/optional-key :baz) s/Key})))
  (is (BarNewStyle. 1 :foo))
  (is (= #{:foo :bar} (set (keys (map->BarNewStyle {:foo 1})))))
  (is (thrown? Exception (map->BarNewStyle {}))) ;; check for primitive long
  (valid! BarNewStyle (BarNewStyle. 1 "test"))
  (invalid! BarNewStyle (BarNewStyle. 1 :foo))
  (valid! BarNewStyle (assoc (BarNewStyle. 1 "test") :baz :foo))
  (invalid! BarNewStyle (assoc (BarNewStyle. 1 "test") :baaaz :foo))
  (invalid! BarNewStyle (assoc (BarNewStyle. 1 "test") :baz "foo")))


;; Now test that schemata and protocols work as type hints.

(def LongOrString (s/either s/Int s/Str))

(sm/defrecord Nested [^Bar4 b ^LongOrString c ^PProtocol p])

(deftest fancier-defrecord-schema-test
  (let [bar1 (Bar. 1 "a")
        bar2 (Bar2. 1 "a")]
    (is (= (s/class-schema Nested)
           (s/record Nested {:b Bar4
                             :c LongOrString
                             :p (s/protocol PProtocol)})))
    (valid! Nested (Nested. (Bar4. [1] {}) 1 bar2))
    (valid! Nested (Nested. (Bar4. [1] {}) "hi" bar2))
    (invalid! Nested (Nested. (Bar4. [1] {}) "hi" bar1))
    (invalid! Nested (Nested. (Bar4. [1] {:foo :bar}) 1 bar2))
    (invalid! Nested (Nested. nil "hi" bar2))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schematized functions

;; helpers

(deftest split-rest-arg-test
  (is (= (schema.macros/split-rest-arg ['a '& 'b])
         '[[a] b]))
  (is (= (schema.macros/split-rest-arg ['a 'b])
         '[[a b] nil])))

;;; fn

(def OddLong (s/both odd? long))

(def +test-fn-schema+
  "Schema for (s/fn ^String [^OddLong x y])"
  (s/=> s/Str OddLong s/Any))

(deftest simple-validated-meta-test
  (let [f (s/fn ^s/Str foo [^OddLong arg0 arg1])]
    (def s2 (s/fn-schema f))
    (is (= +test-fn-schema+ (s/fn-schema f)))))

(deftest simple-validated-fn-test
  (let [f (s/fn test-fn :- even?
            [^s/Int x ^{:s {:foo (s/both s/Int odd?)}} y]
            (+ x (:foo y -100)))]
    (s/with-fn-validation
      (is (= 4 (f 1 {:foo 3})))
      ;; Primitive Interface Test
      #+clj (is (thrown? Exception (.invokePrim f 1 {:foo 3}))) ;; primitive type hints don't work on fns
      (is (thrown? Exception (f 1 {:foo 4}))) ;; foo not odd?
      (is (thrown? Exception (f 2 {:foo 3}))))  ;; return not even?

    (is (= 5 (f 1 {:foo 4}))) ;; foo not odd?
    (is (= 4 (f (Integer. (int 1)) {:foo 3}))) ;; first arg not long
    (is (= 5 (f 2 {:foo 3})))  ;; return not even?
    ))

(defn parse-long [x]
  #+clj (Long/parseLong x)
  #+cljs (js/parseInt x))

(deftest destructured-validated-fn-test
  (let [LongPair [(s/one s/Int "x") (s/one s/Int "y")]
        f (s/fn foo :- s/Int
            [^LongPair [x y] ^s/Int arg1]
            (+ x y arg1))]
    (is (= (s/=> s/Int LongPair s/Int)
           (s/fn-schema f)))
    (s/with-fn-validation
      (is (= 6 (f [1 2] 3)))
      (is (thrown? Exception (f ["a" 2] 3))))))

(deftest two-arity-fn-test
  (let [f (s/fn foo :- s/Int
            ([^s/Str arg0 ^s/Int arg1] (+ arg1 (foo arg0)))
            ([^String arg0] (parse-long arg0)))]
    (is (= (s/=>* s/Int [s/Str] [s/Str s/Int])
           (s/fn-schema f)))
    (is (= 3 (f "3")))
    (is (= 10 (f "3" 7)))))

(deftest infinite-arity-fn-test
  (let [f (s/fn foo :- s/Int
            ([^s/Int arg0] (inc arg0))
            ([^s/Int arg0  & ^{:s [s/Str]} strs]
               (reduce + (foo arg0) (map count strs))))]
    (is (= (s/=>* s/Int [s/Int] [s/Int & [s/Str]])
           (s/fn-schema f)))
    (s/with-fn-validation
      (is (= 5 (f 4)))
      (is (= 16 (f 4 "55555" "666666")))
      (is (thrown? Exception (f 4 [3 3 3]))))))


;;; defn

(def OddLongString
  (s/both s/Str #(odd? (parse-long %))))

(sm/defn ^{:s OddLongString :tag String} simple-validated-defn
  "I am a simple schema fn"
  {:metadata :bla}
  [^OddLong arg0]
  (str arg0))

(sm/defn ^{:tag String} simple-validated-defn-new :- OddLongString
  "I am a simple schema fn"
  {:metadata :bla}
  [arg0 :- OddLong]
  (str arg0))

(def +simple-validated-defn-schema+
  (s/=> OddLongString OddLong))

(deftest simple-validated-defn-test
  (doseq [[label v] {"old" #'simple-validated-defn "new" #'simple-validated-defn-new}]
    (testing label
      (let [{:keys [tag schema doc metadata]} (meta v)]
        #+clj (is (= tag String))
        (is (= +simple-validated-defn-schema+ schema))
        (is (= doc "I am a simple schema fn"))
        (is (= metadata :bla)))
      (is (= +simple-validated-defn-schema+ (s/fn-schema @v)))

      (s/with-fn-validation
        (is (= "3" (@v 3)))
        (is (thrown? Exception (@v 4)))
        (is (thrown? Exception (@v "a"))))

      (is (= "4" (@v 4))))))

;; Primitive validation testing for JVM
#+clj
(do

  (def +primitive-validated-defn-schema+
    (s/=> long OddLong))

  (sm/defn ^long primitive-validated-defn
    [^long ^{:s OddLong} arg0]
    (inc arg0))

  (sm/defn ^long primitive-validated-defn-new :- long
    [^long arg0 :- OddLong]
    (inc arg0))


  (deftest simple-primitive-validated-defn-test
    (doseq [[label f] {"old" primitive-validated-defn "new" primitive-validated-defn-new}]
      (testing label
        (is (= +primitive-validated-defn-schema+ (s/fn-schema f)))

        (is ((ancestors (class f)) clojure.lang.IFn$LL))
        (s/with-fn-validation
          (is (= 4 (f 3)))
          (is (= 4 (.invokePrim f 3)))
          (is (thrown? Exception (f 4))))

        (is (= 5 (f 4)))))))
