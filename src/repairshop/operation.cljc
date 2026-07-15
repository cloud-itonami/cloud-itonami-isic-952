(ns repairshop.operation
  "StateGraph-equivalent operation flow for repair-shop administrative coordination.
  Flow: intake → advise → govern → decide → commit | hold | escalate"
  (:require [repairshop.store :as store]
            [repairshop.advisor :as advisor]
            [repairshop.governor :as governor]))

;; ---------------------- operation flow ----------------------

(defn process-proposal
  "Process a proposal through the full flow:
  1. Advise: enrich with reasoning
  2. Govern: apply three HARD checks
  3. Decide: :COMMIT (auto-approve), :HOLD (escalate for review), or :ESCALATE (safety)
  4. Commit: append to ledger if approved"
  [store proposal]
  (let [advised (advisor/advise proposal)
        governed (governor/govern store advised)
        decision (:decision governed)

        ;; Escalate safety concerns regardless of governance pass
        is-safety-flag (= (:operation proposal) :flag-safety-concern)

        ;; Determine action
        action (cond
                 is-safety-flag :ESCALATE
                 (not (:passes? governed)) :HOLD
                 :else :COMMIT)

        ;; Commit to ledger if approved (non-safety, governance passes)
        _ (when (= action :COMMIT)
            (store/append-log store {:type :operation-committed
                                     :proposal advised
                                     :decision decision}))]

    {:proposal advised
     :governance governed
     :action action
     :escalates? (or is-safety-flag (not (:passes? governed)))}))

(defn schedule-repair-intake
  "Operation: Schedule repair item intake/drop-off appointment.
  Administrative logistics only — never diagnostic assessment."
  [store customer-id item-id proposed-intake-date]
  (let [proposal {:operation :schedule-repair-intake
                  :effect :propose
                  :customer-id customer-id
                  :item-id item-id
                  :proposed-date proposed-intake-date}]
    (process-proposal store proposal)))

(defn coordinate-repair-status-update
  "Operation: Update administrative repair status (logistics tracking).
  Transitions: awaiting-diagnosis → in-progress → ready-for-pickup → completed.
  Never the technical diagnosis or repair-completion sign-off."
  [store item-id new-status]
  (let [proposal {:operation :coordinate-repair-status-update
                  :effect :propose
                  :item-id item-id
                  :new-status new-status}]
    (process-proposal store proposal)))

(defn coordinate-supply-request
  "Operation: Request consumables/office/facility supplies.
  Non-repair-part ordering only."
  [store supply-id quantity requested-by]
  (let [proposal {:operation :coordinate-supply-request
                  :effect :propose
                  :supply-id supply-id
                  :quantity quantity
                  :requested-by requested-by}]
    (process-proposal store proposal)))

(defn schedule-staff-shift-proposal
  "Operation: Propose staff shift (administrative PROPOSAL only, never binding).
  Requires human approval for binding assignment."
  [store staff-id proposed-date shift-type]
  (let [proposal {:operation :schedule-staff-shift-proposal
                  :effect :propose
                  :staff-id staff-id
                  :proposed-date proposed-date
                  :shift-type shift-type}]
    (process-proposal store proposal)))

(defn flag-safety-concern
  "Operation: Flag facility/equipment safety concern for HUMAN review.
  ALWAYS escalates, never auto-committed."
  [store concern-type concern-description]
  (let [proposal {:operation :flag-safety-concern
                  :effect :propose
                  :concern-type concern-type
                  :concern-description concern-description}]
    (process-proposal store proposal)))
