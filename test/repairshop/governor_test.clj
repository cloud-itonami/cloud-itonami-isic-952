(ns repairshop.governor-test
  (:require [clojure.test :refer [deftest testing is]]
            [repairshop.store :as store]
            [repairshop.governor :as governor]))

(deftest item-unverified-rejects
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                    :effect :propose
                                    :item-id :item-004})]
    (is (= :REJECT (:decision result)))))

(deftest effect-not-propose-rejects
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                    :effect :approve
                                    :item-id :item-001})]
    (is (= :REJECT (:decision result)))))

(deftest scope-exclusion-diagnostic-rejects
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                    :effect :propose
                                    :item-id :item-001
                                    :description "Run diagnostic test on device"})]
    (is (= :REJECT (:decision result)))))

(deftest scope-exclusion-warranty-rejects
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                    :effect :propose
                                    :item-id :item-001
                                    :description "Check warranty coverage"})]
    (is (= :REJECT (:decision result)))))

(deftest scope-exclusion-pricing-rejects
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                    :effect :propose
                                    :item-id :item-001
                                    :description "Approve pricing decision"})]
    (is (= :REJECT (:decision result)))))

(deftest safety-concern-allowed-through-scope-check
  (testing "flag-safety-concern is exempt from the scope-exclusion 'safety' substring match"
    (let [s (store/create-store)
          result (governor/govern s {:operation :flag-safety-concern
                                      :effect :propose
                                      :concern "Equipment safety hazard detected"})]
      (is (= :APPROVE (:decision result))))))

(deftest happy-path-approves
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                    :effect :propose
                                    :item-id :item-001})]
    (is (= :APPROVE (:decision result)))))
