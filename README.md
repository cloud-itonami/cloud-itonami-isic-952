# cloud-itonami-isic-952: Repair of Personal and Household Goods

Administrative coordination actor for repair shops — intake scheduling, repair-status logistics tracking, parts/supply coordination.

**Domain**: ISIC 952 (Repair of personal and household goods)

**Scope**: Administrative logistics only. NOT diagnostic/repair-technique decisions, warranty/liability determinations, pricing/quote-approval, or safety-authority overrides.

## Modules

- **store.cljc** — MemStore protocol; customer/item/staff/supplies directory; demo data; append-only ledger
- **advisor.cljc** — Proposal enrichment (deterministic demo); preserves original fields, adds `:advisor-reasoning` + `:confidence`
- **governor.cljc** — Three HARD checks; no overrides; conditional item verification
- **operation.cljc** — StateGraph-equivalent flow: intake → advise → govern → decide → commit | hold | escalate
- **phase.cljc** — Rollout phases 0–3 (which ops auto-commit, which escalate)
- **sim.cljc** — 5 demo scenarios (happy path, hard checks, escalation)
- **test.cljc** — 20 comprehensive test cases (5 store + 7 governor + 5 operation + 3 phase)

## Operations (Closed Allowlist)

- **`:schedule-repair-intake`** — Drop-off/pickup appointment scheduling
- **`:coordinate-repair-status-update`** — Administrative logistics (item at stage X), never technical diagnosis
- **`:coordinate-supply-request`** — Non-repair-part consumables (shop/office supplies)
- **`:schedule-staff-shift-proposal`** — Administrative shift PROPOSAL only (never binding)
- **`:flag-safety-concern`** — Facility/equipment safety for HUMAN review (always escalates)

## Governor: Three HARD, Permanent, Un-Overridable Checks

1. **Item/customer unverified** — Target must exist in store AND be independently `:registered?`/`:verified?`, re-derived every time.
2. **Effect not `:propose`** — Rejected outright. All effects must be `:propose`.
3. **Scope exclusion** — Any proposal touching:
   - Diagnostic/repair-technique decisions
   - Warranty/liability determinations
   - Pricing/quote-approval decisions
   - Safety-authority overrides

   Uses EN+JA substring scan, qualified so `:flag-safety-concern` isn't self-blocked.

## Test Coverage (20 Cases)

- **Store** (5): customer lookup, all-customers, item lookup, all-items, ledger append
- **Governor** (7): item-unverified, effect-not-propose, scope-exclusion (3 variants), safety-concern allowed, happy path
- **Operation** (5): intake happy path, unverified rejection, safety escalation, status update, supply request
- **Phase** (3): phase-0 read-only, phase-1 allows intake, phase-3 auto-commits
- **Scenarios** (5): happy-path intake, unverified item, scope exclusion, safety escalation, status update

All passing.

## Running Tests

```bash
nbb -e "(require '[repairshop.test]) (repairshop.test/run-all-tests)"
```

Expected output: 20 test cases (✓), 5 scenarios (✓)

## Repo Visibility

PUBLIC (GitHub `private: false`, `visibility: public`)

## Registry Entry (kotoba-lang/industry)

Entry 952:
- From: `:maturity :spec`, `:repo nil`, `:business-id nil`, `:required-technologies [:robotics ...]`
- To: `:maturity :implemented`, `:repo "https://github.com/cloud-itonami/cloud-itonami-isic-952"`, `:business-id "cloud-itonami-isic-952"`, `:required-technologies [:identity :forms :dmn :bpmn :audit-ledger]` (`:robotics` stripped)

## References

- ADR-2607154304: cloud-itonami ISIC-952 Repair Shop Actor (this ADR)
- ADR-2607121000: Cloud-itonami Wave-4 rollout plan
- ADR-2607152500: Wave-4 amendment (ADR slot allocation)
- ADR-2607154303: isic-949 design reference (module shape pattern)
- CLAUDE.md: Actors pattern, build-actor skill, registry verification workflow

## License

AGPL-3.0
