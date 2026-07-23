(ns repairshop.operation
  "OperationActor -- one repair-shop administrative-coordination run,
  expressed as a genuinely compiled langgraph-clj StateGraph binding the
  advisor, the governor's three HARD checks, the phase-rollout gate, and
  the append-only ledger.

  One graph run = one operation (intake -> advise -> govern -> decide ->
  commit | hold | request-approval -> commit | hold). No unbounded inner
  loop -- each run is auditable and checkpointed.

  `:flag-safety-concern` is NEVER in any phase's auto-commit set
  (repairshop.phase) and is checked ahead of the phase gate too, so it
  always pauses at `:request-approval` for a human operator, regardless
  of phase or of whether governance happens to pass.

  The five convenience functions below (`schedule-repair-intake` etc.)
  run the graph once per call and never auto-resume an interrupted run
  -- a paused (`:request-approval`) run is reported as `:ESCALATE`,
  matching this actor's existing fire-and-forget calling convention (no
  caller resumes a held approval today)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [repairshop.store :as store]
            [repairshop.advisor :as advisor]
            [repairshop.governor :as governor]
            [repairshop.phase :as phase]))

(defn build
  "Compiles an OperationActor graph bound to `store` (any
  `repairshop.store/MemStore`).
  opts:
    :phase-num    -- rollout phase (default: 3)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [phase-num checkpointer]
             :or   {phase-num    3
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:proposal    {:default nil}
         :advised     {:default nil}
         :governance  {:default nil}
         :disposition {:default nil}
         :approval    {:default nil}}})

      (g/add-node :intake (fn [s] s))

      ;; Advisor inference (the contained intelligence node) -- enriches
      ;; the raw proposal with reasoning/confidence, never writes.
      (g/add-node :advise
        (fn [{:keys [proposal]}]
          {:advised (advisor/advise proposal)}))

      ;; Independent governor -- three HARD, permanent checks.
      (g/add-node :govern
        (fn [{:keys [advised]}]
          {:governance (governor/govern store advised)}))

      ;; Decide: safety flags always pause for a human first; then the
      ;; governor's hard checks; only then the phase-rollout gate.
      (g/add-node :decide
        (fn [{:keys [advised governance]}]
          (let [op (:operation advised)]
            (cond
              (= op :flag-safety-concern)
              {:disposition :request-approval}

              (not (:passes? governance))
              {:disposition :hold}

              (phase/auto-commits-in-phase? phase-num op)
              {:disposition :commit}

              :else
              {:disposition :request-approval}))))

      ;; Request human approval (holds here until external resume).
      (g/add-node :request-approval (fn [s] s))

      ;; Terminal node (commit) -- append to the audit ledger.
      (g/add-node :commit
        (fn [{:keys [advised governance]}]
          (store/append-log store {:type :operation-committed
                                    :proposal advised
                                    :decision (:decision governance)})
          {:disposition :commit}))

      ;; Terminal node (hold) -- no ledger entry, matching this actor's
      ;; existing behavior (only commits are logged). Explicitly sets
      ;; :disposition -- reached both directly from :decide (which
      ;; already set it) AND via a rejected :request-approval resume
      ;; (which didn't), so it can't rely on an upstream node having
      ;; set it.
      (g/add-node :hold (fn [_] {:disposition :hold}))

      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :hold              :hold
            :request-approval  :request-approval
            :commit            :commit))
        {:hold :hold :request-approval :request-approval :commit :commit})

      ;; Approval resumed externally (`{:approval {:status :approved}}`).
      (g/add-conditional-edges :request-approval
        (fn [{:keys [approval]}]
          (if (= :approved (:status approval)) :commit :hold))
        {:commit :commit :hold :hold})

      (g/set-entry-point :intake)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

(defn process-proposal
  "Run one raw proposal map through the compiled graph once, translating
  the result into this actor's `{:action :COMMIT|:HOLD|:ESCALATE ...}`
  shape. A paused (`:request-approval`) run is reported as `:ESCALATE`."
  [store proposal & [opts]]
  (let [{:keys [state status]} (g/run* (build store opts) {:proposal proposal}
                                        {:thread-id (str (random-uuid))})
        {:keys [advised governance disposition]} state
        action (case status
                 :interrupted :ESCALATE
                 (case disposition :commit :COMMIT :hold :HOLD))]
    {:proposal advised
     :governance governance
     :action action
     :escalates? (or (= (:operation advised) :flag-safety-concern)
                      (not (:passes? governance)))}))

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
  ALWAYS pauses for human approval, never auto-committed."
  [store concern-type concern-description]
  (let [proposal {:operation :flag-safety-concern
                  :effect :propose
                  :concern-type concern-type
                  :concern-description concern-description}]
    (process-proposal store proposal)))
