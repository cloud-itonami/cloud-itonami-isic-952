(ns repairshop.advisor
  "Advisor for repair-shop administrative coordination proposals.
  Enriches proposals with deterministic reasoning and confidence scores.
  Demo-only: production requires real LLM with safeguards.")

(defn advise
  "Enrich a proposal with advisor reasoning and confidence.
  Preserves all original proposal fields and adds:
  - :advisor-reasoning (string, deterministic demo logic)
  - :confidence (0.0–1.0, based on proposal completeness)"
  [proposal]
  (let [op (:operation proposal)]
    (case op
      :schedule-repair-intake
      (assoc proposal
        :advisor-reasoning "Item intake scheduling is administrative logistics"
        :confidence 0.95)

      :coordinate-repair-status-update
      (assoc proposal
        :advisor-reasoning "Status update logistics: item transitions through workflow states"
        :confidence 0.85)

      :coordinate-supply-request
      (assoc proposal
        :advisor-reasoning "Consumables and facility supplies are within administrative scope"
        :confidence 0.80)

      :schedule-staff-shift-proposal
      (assoc proposal
        :advisor-reasoning "Staff scheduling is administrative coordination (proposal only)"
        :confidence 0.75)

      :flag-safety-concern
      (assoc proposal
        :advisor-reasoning "Facility concerns flagged for human review and escalation"
        :confidence 0.90)

      ;; unknown op
      (assoc proposal :confidence 0.50))))
