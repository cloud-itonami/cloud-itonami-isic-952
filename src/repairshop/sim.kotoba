(ns repairshop.sim
  "5 demo scenarios for repair-shop administrative coordination."
  (:require [repairshop.store :as store]
            [repairshop.operation :as op]))

;; ---------------------- scenarios ----------------------

(defn scenario-1-happy-path-intake
  "Scenario 1: Happy path — verified customer schedules repair intake."
  []
  (let [shop-store (store/create-store)]
    {
     :name "Happy path: repair intake scheduling"
     :description "Verified customer (cust-001) schedules intake for verified item (item-001)"
     :setup shop-store
     :operation (fn [s] (op/schedule-repair-intake s :cust-001 :item-001 "2026-07-16"))
     :expected-action :COMMIT
     }))

(defn scenario-2-unverified-item
  "Scenario 2: Hard check #1 — unverified item rejected."
  []
  (let [shop-store (store/create-store)]
    {
     :name "Hard check #1: unverified item rejected"
     :description "Unverified item (item-004) rejected outright"
     :setup shop-store
     :operation (fn [s] (op/schedule-repair-intake s :cust-003 :item-004 "2026-07-16"))
     :expected-action :HOLD
     }))

(defn scenario-3-scope-exclusion
  "Scenario 3: Hard check #3 — scope exclusion blocks diagnostic proposal."
  []
  (let [shop-store (store/create-store)
        proposal {:operation :coordinate-repair-status-update
                  :effect :propose
                  :item-id :item-001
                  :description "Customer requested diagnostic to determine repair technique needed"}]
    {
     :name "Hard check #3: scope exclusion (diagnostic)"
     :description "Proposal mentioning 'diagnostic' blocked by scope exclusion"
     :setup shop-store
     :operation (fn [s] (op/process-proposal s proposal))
     :expected-action :HOLD
     }))

(defn scenario-4-safety-escalation
  "Scenario 4: Safety concern always escalates."
  []
  (let [shop-store (store/create-store)]
    {
     :name "Safety escalation: concern always escalates"
     :description "Safety concern flagged, escalates regardless of governance"
     :setup shop-store
     :operation (fn [s] (op/flag-safety-concern s :equipment-hazard "Drill press missing safety guard"))
     :expected-action :ESCALATE
     }))

(defn scenario-5-status-update
  "Scenario 5: Happy path — status update for verified item."
  []
  (let [shop-store (store/create-store)]
    {
     :name "Happy path: repair status update"
     :description "Verified item (item-002) status transitions in-progress → ready-for-pickup"
     :setup shop-store
     :operation (fn [s] (op/coordinate-repair-status-update s :item-002 :ready-for-pickup))
     :expected-action :COMMIT
     }))

;; ---------------------- runner ----------------------

(defn run-all-scenarios
  "Execute all 5 scenarios, return results."
  []
  (let [scenarios [
    (scenario-1-happy-path-intake)
    (scenario-2-unverified-item)
    (scenario-3-scope-exclusion)
    (scenario-4-safety-escalation)
    (scenario-5-status-update)]]

    (map (fn [scenario]
           (let [result ((:operation scenario) (:setup scenario))]
             (assoc scenario
                    :result result
                    :passes? (= (:action result) (:expected-action scenario)))))
         scenarios)))

#?(:clj
   (defn -main [& _args]
     (println "Repair-Shop Administrative Coordination Actor (ISIC 952) - Demo")
     (println "=================================================================")
     (doseq [scenario (run-all-scenarios)]
       (println (if (:passes? scenario) "✓" "✗") (:name scenario)
                 "->" (get-in scenario [:result :action])))))
