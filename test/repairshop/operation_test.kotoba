(ns repairshop.operation-test
  (:require [clojure.test :refer [deftest testing is]]
            [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [repairshop.store :as store]
            [repairshop.operation :as op]))

;; ---------------------- behavioral tests via the 5 named ops ----------------------

(deftest intake-happy-path-commits
  (let [s (store/create-store)
        result (op/schedule-repair-intake s :cust-001 :item-001 "2026-07-16")]
    (is (= :COMMIT (:action result)))
    (is (= 1 (count (store/ledger s))))))

(deftest intake-unverified-item-holds
  (let [s (store/create-store)
        result (op/schedule-repair-intake s :cust-003 :item-004 "2026-07-16")]
    (is (= :HOLD (:action result)))
    (is (empty? (store/ledger s)) "held, nothing committed")))

(deftest safety-concern-always-escalates
  (testing "flag-safety-concern pauses at :request-approval regardless of phase or governance"
    (let [s (store/create-store)
          result (op/flag-safety-concern s :hazard "Drill press guard missing")]
      (is (= :ESCALATE (:action result)))
      (is (true? (:escalates? result)))
      (is (empty? (store/ledger s)) "held at interrupt, nothing committed yet"))))

(deftest status-update-happy-path-commits
  (let [s (store/create-store)
        result (op/coordinate-repair-status-update s :item-002 :ready-for-pickup)]
    (is (= :COMMIT (:action result)))))

(deftest supply-request-happy-path-commits
  (let [s (store/create-store)
        result (op/coordinate-supply-request s :sup-001 10 :staff-001)]
    (is (= :COMMIT (:action result)))))

(deftest each-clean-commit-appends-exactly-one-ledger-entry
  (let [s (store/create-store)]
    (op/schedule-repair-intake s :cust-001 :item-001 "2026-07-16")
    (op/coordinate-repair-status-update s :item-002 :ready-for-pickup)
    (is (= 2 (count (store/ledger s))))))

;; ---------------------- phase-gate tests (via the compiled graph directly) ----------------------

(defn- run-op!
  "Runs the compiled graph once; returns the full run* result
  {:state :events :status :frontier}."
  [store proposal opts thread-id]
  (g/run* (op/build store opts) {:proposal proposal} {:thread-id thread-id}))

(deftest phase-1-does-not-auto-commit-a-normally-safe-op
  (testing "at phase 1, :coordinate-repair-status-update is ALLOWED but not in the auto-commit
  set -- two independent gates (governor + phase) must both agree before auto-commit; here
  the phase table alone doesn't, so it pauses for approval instead of committing"
    (let [s (store/create-store)
          result (run-op! s {:operation :coordinate-repair-status-update
                              :effect :propose :item-id :item-002 :new-status :ready-for-pickup}
                           {:phase-num 1} (str (random-uuid)))]
      (is (= :interrupted (:status result)))
      (is (empty? (store/ledger s))))))

(deftest phase-3-auto-commits-the-same-op
  (testing "the identical proposal, at phase 3, commits cleanly (no approval pause)"
    (let [s (store/create-store)
          result (run-op! s {:operation :coordinate-repair-status-update
                              :effect :propose :item-id :item-002 :new-status :ready-for-pickup}
                           {:phase-num 3} (str (random-uuid)))]
      (is (= :done (:status result)))
      (is (= :commit (get-in result [:state :disposition])))
      (is (= 1 (count (store/ledger s)))))))

;; ---------------------- resume-after-interrupt test ----------------------

(deftest interrupted-run-resumes-to-commit-on-approval
  (testing "a paused :request-approval run genuinely resumes (same checkpointer, same
  thread-id) and reaches :commit once a human approves -- proving the interrupt is a real
  checkpointed pause, not a synchronous dead end"
    (let [s (store/create-store)
          thread-id (str (random-uuid))
          checkpointer (cp/mem-checkpointer)
          opts {:phase-num 3 :checkpointer checkpointer}
          proposal {:operation :flag-safety-concern :effect :propose
                    :concern-type :hazard :concern-description "Drill press guard missing"}
          paused (run-op! s proposal opts thread-id)]
      (is (= :interrupted (:status paused)))
      (let [resumed (g/run* (op/build s opts) {:approval {:status :approved}} {:thread-id thread-id :resume? true})]
        (is (= :done (:status resumed)))
        (is (= :commit (get-in resumed [:state :disposition])))
        (is (= 1 (count (store/ledger s))))))))

(deftest interrupted-run-resumes-to-hold-on-rejection
  (let [s (store/create-store)
        thread-id (str (random-uuid))
        checkpointer (cp/mem-checkpointer)
        opts {:phase-num 3 :checkpointer checkpointer}
        proposal {:operation :flag-safety-concern :effect :propose
                  :concern-type :hazard :concern-description "Drill press guard missing"}
        paused (run-op! s proposal opts thread-id)]
    (is (= :interrupted (:status paused)))
    (let [resumed (g/run* (op/build s opts) {:approval {:status :rejected}} {:thread-id thread-id :resume? true})]
      (is (= :done (:status resumed)))
      (is (= :hold (get-in resumed [:state :disposition])))
      (is (empty? (store/ledger s))))))
