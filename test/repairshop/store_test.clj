(ns repairshop.store-test
  (:require [clojure.test :refer [deftest testing is]]
            [repairshop.store :as store]))

(deftest customer-lookup
  (let [s (store/create-store)]
    (is (= "Alice Johnson" (:name (store/customer s :cust-001))))))

(deftest all-customers-count
  (let [s (store/create-store)]
    (is (= 3 (count (store/all-customers s))))))

(deftest repair-item-lookup
  (let [s (store/create-store)]
    (is (= "Leather shoes - heel replacement" (:description (store/repair-item s :item-001))))))

(deftest all-items-count
  (let [s (store/create-store)]
    (is (= 5 (count (store/all-items s))))))

(deftest ledger-append-and-read
  (testing "append-log appends to the ledger and stamps a timestamp"
    (let [s (store/create-store)]
      (is (empty? (store/ledger s)))
      (store/append-log s {:type :test-entry :value 42})
      (is (= 1 (count (store/ledger s))))
      (is (= :test-entry (:type (first (store/ledger s)))))
      (is (some? (:timestamp (first (store/ledger s))))))))
