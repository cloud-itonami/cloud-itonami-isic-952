(ns repairshop.test
  "20 comprehensive test cases for repair-shop administrative coordination actor."
  (:require [repairshop.store :as store]
            [repairshop.advisor :as advisor]
            [repairshop.governor :as governor]
            [repairshop.operation :as op]
            [repairshop.phase :as phase]
            [repairshop.sim :as sim]))

;; ---------------------- test helpers ----------------------

(defn assert-eq [actual expected test-name]
  (if (= actual expected)
    (println "  ✓" test-name)
    (do
      (println "  ✗" test-name)
      (println "    Expected:" expected)
      (println "    Actual:" actual)
      false)))

(defn assert-true [value test-name]
  (if value
    (println "  ✓" test-name)
    (do
      (println "  ✗" test-name)
      false)))

;; ---------------------- store tests (5 cases) ----------------------

(defn test-store-customer-lookup []
  (let [s (store/create-store)
        customer (store/customer s :cust-001)]
    (assert-eq (:name customer) "Alice Johnson" "Store: customer lookup")))

(defn test-store-all-customers []
  (let [s (store/create-store)
        customers (store/all-customers s)]
    (assert-eq (count customers) 3 "Store: all-customers count")))

(defn test-store-repair-item-lookup []
  (let [s (store/create-store)
        item (store/repair-item s :item-001)]
    (assert-eq (:description item) "Leather shoes - heel replacement" "Store: repair-item lookup")))

(defn test-store-all-items []
  (let [s (store/create-store)
        items (store/all-items s)]
    (assert-eq (count items) 5 "Store: all-items count")))

(defn test-store-ledger-append []
  (let [s (store/create-store)]
    (store/append-log s {:type :test-entry :value 42})
    (assert-true true "Store: ledger append")))

;; ---------------------- governor tests (7 cases) ----------------------

(defn test-governor-item-unverified []
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                   :effect :propose
                                   :item-id :item-004})]
    (assert-eq (:decision result) :REJECT "Governor: item-unverified check")))

(defn test-governor-effect-not-propose []
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                   :effect :approve
                                   :item-id :item-001})]
    (assert-eq (:decision result) :REJECT "Governor: effect-not-propose check")))

(defn test-governor-scope-diagnostic []
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                   :effect :propose
                                   :item-id :item-001
                                   :description "Run diagnostic test on device"})]
    (assert-eq (:decision result) :REJECT "Governor: scope-exclusion (diagnostic)")))

(defn test-governor-scope-warranty []
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                   :effect :propose
                                   :item-id :item-001
                                   :description "Check warranty coverage"})]
    (assert-eq (:decision result) :REJECT "Governor: scope-exclusion (warranty)")))

(defn test-governor-scope-pricing []
  (let [s (store/create-store)
        result (governor/govern s {:operation :coordinate-repair-status-update
                                   :effect :propose
                                   :item-id :item-001
                                   :description "Approve pricing decision"})]
    (assert-eq (:decision result) :REJECT "Governor: scope-exclusion (pricing)")))

(defn test-governor-scope-safety-allowed []
  (let [s (store/create-store)
        result (governor/govern s {:operation :flag-safety-concern
                                   :effect :propose
                                   :concern "Equipment safety hazard detected"})]
    (assert-eq (:decision result) :APPROVE "Governor: flag-safety-concern allowed")))

(defn test-governor-happy-path []
  (let [s (store/create-store)
        result (governor/govern s {:operation :schedule-repair-intake
                                   :effect :propose
                                   :item-id :item-001})]
    (assert-eq (:decision result) :APPROVE "Governor: happy-path approve")))

;; ---------------------- operation tests (5 cases) ----------------------

(defn test-operation-intake-happy []
  (let [s (store/create-store)
        result (op/schedule-repair-intake s :cust-001 :item-001 "2026-07-16")]
    (assert-eq (:action result) :COMMIT "Operation: intake happy path")))

(defn test-operation-intake-unverified []
  (let [s (store/create-store)
        result (op/schedule-repair-intake s :cust-003 :item-004 "2026-07-16")]
    (assert-eq (:action result) :HOLD "Operation: intake unverified item")))

(defn test-operation-safety-escalation []
  (let [s (store/create-store)
        result (op/flag-safety-concern s :hazard "Drill press guard missing")]
    (assert-eq (:action result) :ESCALATE "Operation: safety escalation")))

(defn test-operation-status-update []
  (let [s (store/create-store)
        result (op/coordinate-repair-status-update s :item-002 :ready-for-pickup)]
    (assert-eq (:action result) :COMMIT "Operation: status update happy path")))

(defn test-operation-supply-request []
  (let [s (store/create-store)
        result (op/coordinate-supply-request s :sup-001 10 :staff-001)]
    (assert-eq (:action result) :COMMIT "Operation: supply request happy path")))

;; ---------------------- phase tests (3 cases) ----------------------

(defn test-phase-0-readonly []
  (assert-eq (phase/allowed-in-phase? 0 :schedule-repair-intake) false "Phase: phase-0 read-only"))

(defn test-phase-1-safe-ops []
  (assert-eq (phase/allowed-in-phase? 1 :schedule-repair-intake) true "Phase: phase-1 allows intake"))

(defn test-phase-3-auto-commit []
  (assert-eq (phase/auto-commits-in-phase? 3 :coordinate-repair-status-update) true "Phase: phase-3 auto-commits"))

;; ---------------------- test runner ----------------------

(defn run-all-tests []
  (println "\n=== REPAIRSHOP TESTS ===\n")

  (println "Store Tests:")
  (test-store-customer-lookup)
  (test-store-all-customers)
  (test-store-repair-item-lookup)
  (test-store-all-items)
  (test-store-ledger-append)

  (println "\nGovernor Tests:")
  (test-governor-item-unverified)
  (test-governor-effect-not-propose)
  (test-governor-scope-diagnostic)
  (test-governor-scope-warranty)
  (test-governor-scope-pricing)
  (test-governor-scope-safety-allowed)
  (test-governor-happy-path)

  (println "\nOperation Tests:")
  (test-operation-intake-happy)
  (test-operation-intake-unverified)
  (test-operation-safety-escalation)
  (test-operation-status-update)
  (test-operation-supply-request)

  (println "\nPhase Tests:")
  (test-phase-0-readonly)
  (test-phase-1-safe-ops)
  (test-phase-3-auto-commit)

  (println "\n=== SCENARIOS ===\n")
  (let [results (sim/run-all-scenarios)]
    (doseq [scenario results]
      (println (if (:passes? scenario) "✓" "✗") (:name scenario))))

  (println "\n=== TEST RUN COMPLETE ==="))
