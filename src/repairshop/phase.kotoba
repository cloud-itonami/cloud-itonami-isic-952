(ns repairshop.phase
  "Rollout phases for repair-shop administrative coordination actor.
  Phases 0–3 define which operations auto-commit and which escalate.")

(defn allowed-in-phase?
  "Check if an operation is allowed in the given phase.

  Phase 0 (read-only): no operations allowed
  Phase 1 (safe ops): intake scheduling, status updates, supply requests
  Phase 2 (extended): adds staff shift proposals
  Phase 3 (full): all operations allowed (safety concerns always escalate)"
  [phase operation]
  (case phase
    0 false  ;; read-only
    1 (boolean (#{:schedule-repair-intake
                  :coordinate-repair-status-update
                  :coordinate-supply-request} operation))
    2 (boolean (#{:schedule-repair-intake
                  :coordinate-repair-status-update
                  :coordinate-supply-request
                  :schedule-staff-shift-proposal} operation))
    3 true   ;; all operations allowed (safety concerns still escalate)
    false))

(defn auto-commits-in-phase?
  "Check if an operation auto-commits without escalation in the given phase.

  :flag-safety-concern ALWAYS escalates, never auto-commits.
  Other operations auto-commit in phase 3 only; escalate in phases 1–2."
  [phase operation]
  (and (allowed-in-phase? phase operation)
       (not= operation :flag-safety-concern)
       (= phase 3)))

(defn describe-phase
  "Human-readable description of rollout phase."
  [phase]
  (case phase
    0 "Phase 0: Read-only (monitoring)"
    1 "Phase 1: Safe operations (intake, status, supplies)"
    2 "Phase 2: Extended operations (adds staff shifts)"
    3 "Phase 3: Full operations (auto-commits safe ops, escalates safety concerns)"
    "Unknown phase"))
