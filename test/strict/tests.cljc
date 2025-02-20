(ns strict.tests
  (:require #?(:cljs [cljs.test :as t]
               :clj [clojure.test :as t])
            [strict.core :as st]))

;; --- Tests

(t/deftest test-optional-validators
  (let [scheme {:max st/number
                :scope st/string}
        input {:scope "foobar"}
        result (st/validate input scheme)]
    (t/is (= nil (first result)))
    (t/is (= input (second result)))))

(t/deftest test-simple-validators
  (let [scheme {:max st/number
                :scope st/string}
        input {:scope "foobar" :max "d"}
        errors {:max "must be a number"}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:scope "foobar"} (second result)))))

(t/deftest test-neested-validators
  (let [scheme {[:a :b] st/number
                [:c :d :e] st/string}
        input {:a {:b "foo"} :c {:d {:e "bar"}}}
        errors {:a {:b "must be a number"}}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:c {:d {:e "bar"}}} (second result)))))

(t/deftest test-single-validators
  (let [result1 (st/validate-single 2 st/number)
        result2 (st/validate-single nil st/number)
        result3 (st/validate-single nil [st/required st/number])]
    (t/is (= [nil 2] result1))
    (t/is (= [nil nil] result2))
    (t/is (= ["this field is mandatory" nil] result3))))

(t/deftest test-parametric-validators
  (let [result1 (st/validate
                 {:name "foo"}
                 {:name [[st/min-count 4]]})
        result2 (st/validate
                 {:name "bar"}
                 {:name [[st/max-count 2]]})]
    (t/is (= {:name "less than the minimum 4"} (first result1)))
    (t/is (= {:name "longer than the maximum 2"} (first result2)))))

(t/deftest test-simple-validators-with-vector-schema
  (let [scheme [[:max st/number]
                [:scope st/string]]
        input {:scope "foobar" :max "d"}
        errors {:max "must be a number"}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:scope "foobar"} (second result)))))

(t/deftest test-simple-validators-with-translate
  (let [scheme [[:max st/number]
                [:scope st/string]]
        input {:scope "foobar" :max "d"}
        errors {:max "a"}
        result (st/validate input scheme {:translate (constantly "a")})]
    (t/is (= errors (first result)))
    (t/is (= {:scope "foobar"} (second result)))))

(t/deftest test-dependent-validators-1
  (let [scheme [[:password1 st/string]
                [:password2 [st/identical-to :password1]]]
        input {:password1 "foobar"
               :password2 "foobar."}
        errors {:password2 "does not match"}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:password1 "foobar"} (second result)))))

(t/deftest test-dependent-validators-2
  (let [scheme [[:password1 st/string]
                [:password2 [st/identical-to :password1]]]
        input {:password1 "foobar"
               :password2 "foobar"}
        result (st/validate input scheme)]
    (t/is (= nil (first result)))
    (t/is (= {:password1 "foobar"
              :password2 "foobar"} (second result)))))

(t/deftest test-multiple-validators
  (let [scheme {:max [st/required st/number]
                :scope st/string}
        input {:scope "foobar"}
        errors {:max "this field is mandatory"}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:scope "foobar"} (second result)))))

(t/deftest test-validation-with-coersion
  (let [scheme {:max st/integer-str
                :scope st/string}
        input {:max "2" :scope "foobar"}
        result (st/validate input scheme)]
    (t/is (= nil (first result)))
    (t/is (= {:max 2 :scope "foobar"} (second result)))))

(t/deftest test-validation-with-custom-coersion
  (let [scheme {:max [[st/number-str :coerce (constantly :foo)]]
                :scope st/string}
        input {:max "2" :scope "foobar"}
        result (st/validate input scheme)]
    (t/is (= nil (first result)))
    (t/is (= {:max :foo :scope "foobar"} (second result)))))

(t/deftest test-validation-with-custom-message
  (let [scheme {:max [[st/number-str :message "custom msg"]]
                :scope st/string}
        input {:max "g" :scope "foobar"}
        errors {:max "custom msg"}
        result (st/validate input scheme)]
    (t/is (= errors (first result)))
    (t/is (= {:scope "foobar"} (second result)))))

(t/deftest test-coersion-with-valid-values
  (let [scheme {:a st/number-str
                :b st/integer-str}
        input {:a 2.3 :b 3.3}
        [errors data] (st/validate input scheme)]
    (t/is (= {:a 2.3 :b 3} data))))

(t/deftest test-validation-nested-data-in-a-vector
  (let [scheme {:a [st/vector [st/every number?]]}
        input1 {:a [1 2 3 4]}
        input2 {:a [1 2 3 4 "a"]}
        [errors1 data1] (st/validate input1 scheme)
        [errors2 data2] (st/validate input2 scheme)]
    (t/is (= data1 input1))
    (t/is (= errors1 nil))
    (t/is (= data2 {}))
    (t/is (= errors2 {:a "must match the predicate"}))))

(t/deftest test-in-range-validator
  (t/is (= {:age "not in range 18 and 26"}
           (-> {:age 17}
               (st/validate {:age [[st/in-range 18 26]]})
               first))))

(t/deftest test-honor-nested-data
  (let [scheme          {[:a :b] [st/required
                                  st/string
                                  [st/min-count 2 :message "foobar"]
                                  [st/max-count 5]]}
        input1          {:a {:b "abcd"}}
        input2          {:a {:b "abcdefgh"}}
        input3          {:a {:b "a"}}
        [errors1 data1] (st/validate input1 scheme)
        [errors2 data2] (st/validate input2 scheme)
        [errors3 data3] (st/validate input3 scheme)]
    (t/is (= data1 input1))
    (t/is (= errors1 nil))
    (t/is (= data2 {}))
    (t/is (= errors2 {:a {:b "longer than the maximum 5"}}))
    (t/is (= data3 {}))
    (t/is (= errors3 {:a {:b "foobar"}}))))

(t/deftest test-nested
  (let [schema {:name  [[st/nested {:first st/string :last st/string}]]
                :age st/integer
                :division [[st/nested {:department [[st/nested {:name st/string
                                                                :dev st/boolean-str}]]}]]}
        input1 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name "product" :dev "true"}}}
        input2 {:name {:first :a :last 1}
                :age "12"
                :division {:department {:name :product :dev true}}}
        input3 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name 111 :dev "true"}}}
        [error1 data1] (st/validate input1 schema)
        [error2 data2] (st/validate input2 schema)
        [error3 data3] (st/validate input3 schema)]

    (t/is (= nil error1))
    (t/is (= {:name {:first "First" :last "Name"}
              :age 12
              :division {:department {:name "product" :dev true}}}
             data1))

    (t/is (= {:name {:first "must be a string" :last "must be a string"}
              :age "must be a integer"
              :division {:department {:name "must be a string" :dev "must be a boolean"}}}
             error2))
    (t/is (= {} data2))

    (t/is (= {:division {:department {:name "must be a string"}}}
             error3))
    (t/is (= {:name {:first "First", :last "Name"}, :age 12, :division {:department {:dev true}}}
             data3))))


(t/deftest test-automatic-nested-conversion
  (let [schema {:name  {:first st/string :last st/string}
                :age st/integer
                :division {:department {:name st/string
                                        :dev st/boolean-str}}}
        input1 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name "product" :dev "true"}}}
        input2 {:name {:first :a :last 1}
                :age "12"
                :division {:department {:name :product :dev true}}}
        input3 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name 111 :dev "true"}}}
        [error1 data1] (st/validate input1 schema)
        [error2 data2] (st/validate input2 schema)
        [error3 data3] (st/validate input3 schema)]

    (t/is (= nil error1))
    (t/is (= {:name {:first "First" :last "Name"}
              :age 12
              :division {:department {:name "product" :dev true}}}
             data1))

    (t/is (= {:name {:first "must be a string" :last "must be a string"}
              :age "must be a integer"
              :division {:department {:name "must be a string" :dev "must be a boolean"}}}
             error2))
    (t/is (= {} data2))

    (t/is (= {:division {:department {:name "must be a string"}}}
             error3))
    (t/is (= {:name {:first "First", :last "Name"}, :age 12, :division {:department {:dev true}}}
             data3))))


(t/deftest test-automatic-nested-conversion-in-validation-vector
  (let [schema {:name  [st/required {:first st/string :last st/string}]
                :age st/integer
                :division [st/required {:department {:name st/string
                                                     :dev st/boolean-str}}]}
        input1 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name "product" :dev "true"}}}
        input2 {:name {:first :a :last 1}
                :age "12"
                :division {:department {:name :product :dev true}}}
        input3 {:name {:first "First" :last "Name"}
                :age 12
                :division {:department {:name 111 :dev "true"}}}
        [error1 data1] (st/validate input1 schema)
        [error2 data2] (st/validate input2 schema)
        [error3 data3] (st/validate input3 schema)]

    (t/is (= nil error1))
    (t/is (= {:name {:first "First" :last "Name"}
              :age 12
              :division {:department {:name "product" :dev true}}}
             data1))

    (t/is (= {:name {:first "must be a string" :last "must be a string"}
              :age "must be a integer"
              :division {:department {:name "must be a string" :dev "must be a boolean"}}}
             error2))
    (t/is (= {} data2))

    (t/is (= {:division {:department {:name "must be a string"}}}
             error3))
    (t/is (= {:name {:first "First", :last "Name"}, :age 12, :division {:department {:dev true}}}
             data3))))

(t/deftest test-nested-on-not-map-data
  (let [schema {:name  [[st/nested {:first st/string :last st/string}]]
                :age st/integer
                :division [[st/nested {:department [[st/nested {:name st/string
                                                                :dev st/boolean-str}]]}]]}
        input1 {:name [1 2 3]
                :age 12
                :division {:department {:name "product" :dev "true"}}}

        [error1 data1] (st/validate input1 schema)]

    (t/is (= {:name "must be a map"} error1))
    (t/is (= {:age 12
              :division {:department {:name "product" :dev true}}}
             data1))))


(t/deftest test-coll-of-validator
  (let [schema {:age [[st/coll-of [st/integer [st/in-range 10 20]]]]}
        input1 {:age [10 20 15]}
        input2 {:age [10 20 21]}
        [error1 data1] (st/validate input1 schema)
        [error2 data2] (st/validate input2 schema)]

    (t/is (= nil error1))
    (t/is (= {:age [10 20 15]} data1))

    (t/is (= {:age [nil nil "not in range 10 and 20"]} error2))
    (t/is (= {:age [10 20 nil]} data2))))


(t/deftest test-coll-of-validator-automatic-nested-conversion
  (let [schema {:people [[st/coll-of {:name [st/string] :age [st/integer-str]}]]}
        input1 {:people [{:name "Test1" :age 20} {:name "Test2" :age "21"}]}
        input2 {:people [{:name "Test1" :age 20} {:name "Test2" :age "abc"}]}
        [error1 data1] (st/validate input1 schema)
        [error2 data2] (st/validate input2 schema)]

    (t/is (= nil error1))
    (t/is (= {:people [{:name "Test1" :age 20} {:name "Test2" :age 21}]} data1))

    (t/is (= {:people [nil {:age "must be a long"}]} error2))
    (t/is (= {:people [{:name "Test1" :age 20} {:name "Test2"}]} data2))))

(t/deftest test-coll-of-validator-on-not-list-data
  (let [schema {:age [[st/coll-of [st/integer [st/in-range 10 20]]]]}
        input1 {:age {:test 1}}
        [error1 data1] (st/validate input1 schema)]

    (t/is (= {:age "must be a list"} error1))
    (t/is (= {} data1))))

(t/deftest test-coll-of-validator-on-empty-list-with-allow-empty-option
  (let [schema {:age [[st/coll-of [st/integer] :allow-empty true]]}
        input1 {:age []}
        [error1 data1] (st/validate input1 schema)]

    (t/is (= nil error1))
    (t/is (= {:age []} data1))))

(t/deftest test-coll-of-validator-on-empty-list-with-allow-empty-false-option
  (let [schema {:age [[st/coll-of [st/integer] :allow-empty false]]}
        input1 {:age []}
        [error1 data1] (st/validate input1 schema)]

    (t/is (= {:age "must not be empty"} error1))
    (t/is (= {} data1))))

(t/deftest test-coll-of-validator-on-empty-list-without-allow-empty-option
  (let [schema {:age [[st/coll-of [st/integer]]]}
        input1 {:age []}
        [error1 data1] (st/validate input1 schema)]

    (t/is (= nil error1))
    (t/is (= {:age []} data1))))

;; --- Entry point

#?(:cljs
   (do
     (enable-console-print!)
     (set! *main-cli-fn* #(t/run-tests))))

#?(:cljs
   (defmethod t/report [:cljs.test/default :end-run-tests]
     [m]
     (if (t/successful? m)
       (set! (.-exitCode js/process) 0)
       (set! (.-exitCode js/process) 1))))
