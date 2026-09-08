(ns repairshop.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo page
  and no generator at all. This namespace drives the REAL actor stack
  (`repairshop.operation` -> `repairshop.advisor` -> `repairshop.governor`
  -> `repairshop.store`, compiled as a langgraph-clj StateGraph and run
  via `langgraph.graph/run*` inside `operation/process-proposal`) against
  a fresh `repairshop.store/create-store` seed, and renders whatever that
  run actually produced.

  Nothing on the page is hand-typed telemetry. Every id, name, phone,
  address, description, intake date, status, stock level and violation
  string is read back out of `repairshop.store` or out of the governor's
  own return value. Every phase/gate cell is computed by calling
  `repairshop.phase` at render time rather than being transcribed.

  Determinism: `repairshop.store/append-log` stamps each ledger entry
  with `System/currentTimeMillis`, and `operation/process-proposal`
  generates a `random-uuid` thread-id per run. Neither is rendered --
  the page is byte-identical across reruns against the same seed (verify
  by diffing two consecutive runs into scratch dirs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [kotoba.lang.text :as str]
            [repairshop.store :as store]
            [repairshop.operation :as op]
            [repairshop.phase :as phase]))

(def ^:private phase-num
  "Rollout phase this console demonstrates. `operation/build` defaults to
  3; stated explicitly here because the phase-gate section reports it."
  3)

;; ----------------------------- scenario -----------------------------

(defn- run-op!
  "Runs one proposal through the real compiled graph and turns the
  result into a journal fact. `label` is only used for display; every
  other field comes back out of the actor."
  [db label proposal]
  (let [{:keys [governance action] :as result} (op/process-proposal db proposal)]
    {:t (case action
          :COMMIT   :committed
          :HOLD     :governor-hold
          :ESCALATE :approval-requested)
     :label label
     :op (:operation proposal)
     :subject (or (:item-id proposal) (:supply-id proposal)
                  (:staff-id proposal) (:customer-id proposal)
                  (:concern-type proposal))
     ;; Every directory entity this proposal touched, so a run shows up
     ;; against the customer as well as against the item it names.
     :refs (into #{} (keep proposal) [:item-id :supply-id :staff-id :customer-id])
     :action action
     :decision (:decision governance)
     :confidence (get-in result [:proposal :confidence])
     :violations (vec (:violations governance))}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches every
  disposition this actor can produce, and exercises all THREE of the
  governor's HARD checks against this repo's own seed data:

    * item-001 (Alice Johnson's leather shoes) takes a clean intake
      booking -- auto-commits at phase 3.
    * item-002 (Bob Smith's wristwatch) moves in-progress ->
      ready-for-pickup -- auto-commits.
    * sup-002 (office packing tape) is restocked and staff-001
      (Tom Wilson) is proposed a morning shift -- both auto-commit;
      neither carries an :item-id, so HARD check 1 is skipped for them.
    * item-004 (Carol Davis's microwave oven) HARD-holds on check 1:
      the seed marks both cust-003 and item-004 :registered? false, and
      the governor re-derives that from the store rather than trusting
      the proposal.
    * item-001 HARD-holds on check 3 when asked to move INTO its own
      seeded `:awaiting-diagnosis` status -- the scope-exclusion scan
      matches /diagnosis/ in the proposal. This is real, slightly
      awkward, actor behaviour and is reported rather than papered over.
    * item-003 (Alice Johnson's dining chair) HARD-holds on check 3 for
      a note asking about warranty coverage -- a second, distinct
      scope-exclusion trigger.
    * item-005 (Bob Smith's jewelry pendant) HARD-holds on check 2:
      `:effect :execute` is rejected outright.
    * a drill-press guard concern escalates -- `:flag-safety-concern` is
      never in any phase's auto-commit set and is checked ahead of the
      phase gate, so it pauses at `:request-approval` for a human.

  Returns `{:store db :journal [...]}`. HARD holds never reach a human."
  []
  (let [db (store/create-store)
        journal
        [(run-op! db "Intake booking - leather shoes"
                  {:operation :schedule-repair-intake :effect :propose
                   :customer-id :cust-001 :item-id :item-001
                   :proposed-date "2026-07-16"})

         (run-op! db "Status update - wristwatch ready"
                  {:operation :coordinate-repair-status-update :effect :propose
                   :item-id :item-002 :new-status :ready-for-pickup})

         (run-op! db "Restock office packing tape"
                  {:operation :coordinate-supply-request :effect :propose
                   :supply-id :sup-002 :quantity 6 :requested-by :staff-002})

         (run-op! db "Shift proposal - Tom Wilson"
                  {:operation :schedule-staff-shift-proposal :effect :propose
                   :staff-id :staff-001 :proposed-date "2026-07-18"
                   :shift-type :morning})

         (run-op! db "Intake booking - microwave oven"
                  {:operation :schedule-repair-intake :effect :propose
                   :customer-id :cust-003 :item-id :item-004
                   :proposed-date "2026-07-16"})

         (run-op! db "Status update - back to awaiting-diagnosis"
                  {:operation :coordinate-repair-status-update :effect :propose
                   :item-id :item-001 :new-status :awaiting-diagnosis})

         (run-op! db "Status update - warranty question"
                  {:operation :coordinate-repair-status-update :effect :propose
                   :item-id :item-003 :new-status :completed
                   :note "Customer asks whether the leg repair is covered by warranty"})

         (run-op! db "Direct status execution - jewelry pendant"
                  {:operation :coordinate-repair-status-update :effect :execute
                   :item-id :item-005 :new-status :completed})

         (run-op! db "Safety concern - drill press guard"
                  {:operation :flag-safety-concern :effect :propose
                   :concern-type :equipment-hazard
                   :concern-description "Drill press missing safety guard"})]]
    {:store db :journal journal}))

;; ----------------------------- derived probes -----------------------------

(def ^:private approver-keys
  "Keys any caller could plausibly use to attribute an approval."
  [:approved-by :approver :approval :by :operator :actor-id])

(defn- approver-in
  "Returns the first approver key actually present in a ledger entry (or
  in the proposal it carries), else nil. Probed at render time so the
  page self-corrects if the pipeline later starts supplying one."
  [entry]
  (some (fn [k] (when (or (contains? entry k)
                          (contains? (:proposal entry) k)) k))
        approver-keys))

(defn- retention-probe
  "Derived, not asserted: what does `store/append-log` actually keep of
  what the graph's `:commit` node hands it? The commit node supplies
  `{:type :proposal :decision}`; anything missing from a ledger entry
  was dropped by the store, anything extra was added by it."
  [ledger]
  (let [supplied #{:type :proposal :decision}
        retained (if (seq ledger) (set (keys (first ledger))) #{})]
    {:supplied supplied
     :retained retained
     :dropped (sort (remove retained supplied))
     :added (sort (remove supplied retained))
     :approvers (->> ledger (keep approver-in) distinct sort vec)}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- yes-no [b]
  (if b "<span class=\"ok\">yes</span>" "<span class=\"critical\">no</span>"))

(defn- by-id [coll] (sort-by (comp str :id) coll))

(defn- journal-for
  "Every journal fact that referenced entity `id`, in run order. Matches
  on the full reference set, not just the nominal subject, so an intake
  booking shows against the customer as well as the item."
  [journal id]
  (filter #(contains? (:refs %) id) journal))

(defn- outcome-cell [journal id]
  (let [facts (journal-for journal id)]
    (cond
      (empty? facts) "<span class=\"muted\">no activity this run</span>"
      :else
      (str/join "<br>"
        (for [{:keys [t violations]} facts]
          (case t
            :committed "<span class=\"ok\">committed</span>"
            :approval-requested "<span class=\"warn\">awaiting human approval</span>"
            :governor-hold
            (str "<span class=\"critical\">HARD hold &middot; "
                 (esc (kw (:check/id (first violations)))) "</span>")))))))

(defn- item-row [db journal {:keys [id customer-id description intake-date
                                    status registered? verified?]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td class=\"num\">%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (kw id))
          (esc (:name (store/customer db customer-id)))
          (esc description)
          (esc intake-date)
          (esc (kw status))
          (yes-no registered?)
          (yes-no verified?)
          (outcome-cell journal id)))

(defn- customer-row [journal {:keys [id name phone address registered? verified?]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td class=\"num\">%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (kw id)) (esc name) (esc phone) (esc address)
          (yes-no registered?) (yes-no verified?)
          (outcome-cell journal id)))

(defn- staff-row [journal {:keys [id name role certified?]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (kw id)) (esc name) (esc (kw role)) (yes-no certified?)
          (outcome-cell journal id)))

(defn- supply-row [journal {:keys [id name category stock]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td class=\"num\">%s</td><td>%s</td></tr>"
          (esc (kw id)) (esc name) (esc (kw category)) stock
          (outcome-cell journal id)))

(def ^:private ops
  "The closed op set this actor exposes (`repairshop.operation`'s five
  public entry points). Only the op names are listed here -- every gate
  cell below is computed by calling `repairshop.phase`."
  [:schedule-repair-intake
   :coordinate-repair-status-update
   :coordinate-supply-request
   :schedule-staff-shift-proposal
   :flag-safety-concern])

(defn- gate-cell [p o]
  (cond
    (phase/auto-commits-in-phase? p o) "<span class=\"ok\">auto-commit</span>"
    (phase/allowed-in-phase? p o) "<span class=\"warn\">human approval</span>"
    :else "<span class=\"muted\">blocked</span>"))

(defn- gate-row [o]
  (format "        <tr><td><code>:%s</code></td>%s</tr>"
          (esc (kw o))
          (str/join (for [p (range 0 4)]
                      (format "<td>%s</td>" (gate-cell p o))))))

(def ^:private hard-checks
  "The governor's three HARD checks, keyed by the `:check/id` it emits.
  The count and the violation text shown for each are read back from the
  run, not transcribed."
  [[:item-unverified "Target item must exist in the store AND be independently :registered? and :verified?, re-derived every time -- never taken from the proposal's self-report."]
   [:effect-not-propose "Effect must be :propose. Any other effect is rejected outright."]
   [:scope-exclusion "Blocks diagnostic / repair-technique decisions, warranty / liability determinations, pricing / quote-approval decisions and safety-authority overrides (EN + JA substring scan)."]])

(defn- check-row [journal [check-id blurb]]
  (let [hits (filter (fn [f] (some #(= check-id (:check/id %)) (:violations f))) journal)
        texts (->> hits
                   (mapcat :violations)
                   (filter #(= check-id (:check/id %)))
                   (map :violation) distinct sort)]
    (format "        <tr><td><code>:%s</code></td><td>%s</td><td class=\"num\">%s</td><td>%s</td></tr>"
            (esc (kw check-id)) (esc blurb) (count hits)
            (if (seq texts)
              (str/join "<br>" (map #(str "<span class=\"critical\">" (esc %) "</span>") texts))
              "<span class=\"muted\">not triggered this run</span>"))))

(defn- journal-row [{:keys [label op subject action decision confidence violations]}]
  (format "        <tr><td>%s</td><td><code>:%s</code></td><td><code>%s</code></td><td class=\"num\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc label) (esc (kw op)) (esc (kw subject))
          (if confidence (format "%.2f" (double confidence)) "&mdash;")
          (case action
            :COMMIT "<span class=\"ok\">COMMIT</span>"
            :HOLD "<span class=\"critical\">HOLD</span>"
            :ESCALATE "<span class=\"warn\">ESCALATE</span>")
          (esc (kw decision))
          (if (seq violations)
            (str/join "<br>" (map #(esc (kw (:check/id %))) violations))
            "<span class=\"muted\">&mdash;</span>")))

(defn- ledger-row [entry]
  (let [p (:proposal entry)
        ak (approver-in entry)]
    (format "        <tr><td><code>%s</code></td><td><code>:%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc (kw (:type entry)))
            (esc (kw (:operation p)))
            (esc (:advisor-reasoning p))
            (esc (kw (:decision entry)))
            (if ak
              (str "<code>" (esc (kw ak)) "</code>")
              "<span class=\"muted\">&mdash;</span>"))))

(defn- approver-note
  "Honest, derived disclosure about approver attribution -- the reader
  must be able to tell 'nobody approved' from 'the store dropped it'."
  [journal probe]
  (let [pending (count (filter #(= :approval-requested (:t %)) journal))]
    (cond
      (seq (:approvers probe))
      (format "Approver attribution IS retained: ledger entries carry %s."
              (esc (str/join ", " (map kw (:approvers probe)))))

      (pos? pending)
      (format (str "No approver is shown because none was ever produced, not because the store dropped one. "
                   "%s run(s) paused at <code>:request-approval</code> and this actor never auto-resumes "
                   "(<code>operation/process-proposal</code> reports a paused run as <code>:ESCALATE</code> "
                   "and returns), so no approval was granted and the <code>:commit</code> node -- the only "
                   "writer to this ledger -- was never reached for them. The <code>:commit</code> node also "
                   "supplies no approver key of its own. This page probes each ledger entry for %s on every "
                   "build, so it will start showing an approver the moment one is supplied.")
              pending
              (esc (str/join " / " (map kw approver-keys))))

      :else "No approvals were requested in this run.")))

(defn- retention-note [probe]
  (format (str "The <code>:commit</code> node hands <code>store/append-log</code> %s. "
               "Reading the ledger back, the store retained %s, dropped %s and added %s. "
               "Measured on this build, not assumed.")
          (esc (str/join ", " (map kw (sort (:supplied probe)))))
          (esc (str/join ", " (map kw (sort (:retained probe)))))
          (if (seq (:dropped probe))
            (str "<span class=\"critical\">" (esc (str/join ", " (map kw (:dropped probe)))) "</span>")
            "nothing")
          (if (seq (:added probe))
            (esc (str/join ", " (map kw (:added probe))))
            "nothing")))

(defn render
  "Renders the full operator-console.html document from the result of
  `run-demo!` (or any other real run of this actor)."
  [{:keys [store journal]}]
  (let [db store
        ledger (vec (store/ledger db))
        probe (retention-probe ledger)
        holds (filter #(= :governor-hold (:t %)) journal)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-952 &middot; repair-shop administrative coordination</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Repair of personal &amp; household goods (ISIC 952) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · " (count holds) " HARD holds this run</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>How this page was produced</h2>\n"
     "    <p class=\"muted\">Build-time output of <code>repairshop.render-html</code> (<code>clojure -M:dev:render-html</code>). "
     "It runs the real compiled langgraph-clj StateGraph in <code>repairshop.operation</code> against a fresh "
     "<code>repairshop.store/create-store</code> seed and prints what came back. No mock data and no hand-written rows: "
     "every id, name, phone, address, description, date, status and violation string below is read out of the store or out of "
     "the governor's own return value, and every phase-gate cell is computed by calling <code>repairshop.phase</code> while "
     "rendering. The build fails if the scenario produces zero HARD governor holds.</p>\n"
     "    <p class=\"muted\">Ledger timestamps and per-run thread-ids exist but are deliberately not rendered, so the page is "
     "byte-identical across reruns against the same seed.</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Repair items</h2>\n"
     "    <p class=\"muted\">Customer name is joined from the customer directory via each item's <code>:customer-id</code>. "
     "&ldquo;Registered / verified&rdquo; are the item's own fields — the two the governor re-derives for HARD check 1.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Item</th><th>Customer</th><th>Description</th><th>Intake</th><th>Seeded status</th><th>Registered</th><th>Verified</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial item-row db journal) (by-id (store/all-items db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Customer directory</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Customer</th><th>Name</th><th>Phone</th><th>Address</th><th>Registered</th><th>Verified</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial customer-row journal) (by-id (store/all-customers db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Staff</h2>\n"
     "    <p class=\"muted\">Shift proposals are administrative proposals only, never a binding assignment.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Staff</th><th>Name</th><th>Role</th><th>Certified</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial staff-row journal) (by-id (store/all-staff db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Supplies</h2>\n"
     "    <p class=\"muted\">Consumables, office and facility supplies only — never repair parts. Stock is the seeded level; "
     "this actor proposes restocks and does not mutate stock.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Supply</th><th>Name</th><th>Category</th><th>Stock</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial supply-row journal) (by-id (store/all-supplies db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor — three HARD checks</h2>\n"
     "    <p class=\"muted\">HARD holds are permanent and have no override path. A held run writes nothing to the audit "
     "ledger and never reaches a human. Violation text below is the governor's own, captured from this run.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Check</th><th>Rule</th><th>Fired</th><th>Violation (verbatim)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial check-row journal) hard-checks)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Rollout phase gate</h2>\n"
     "    <p class=\"muted\">This console runs at <strong>" (esc (phase/describe-phase phase-num)) "</strong>. "
     "Every cell is computed live by <code>repairshop.phase/allowed-in-phase?</code> and "
     "<code>auto-commits-in-phase?</code>. <code>:flag-safety-concern</code> is in no phase's auto-commit set and is "
     "checked ahead of the phase gate, so it always pauses for a human.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Phase 0</th><th>Phase 1</th><th>Phase 2</th><th>Phase 3</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map gate-row ops)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Run journal</h2>\n"
     "    <p class=\"muted\">Every graph run this scenario performed, in order. Confidence is the advisor's; the decision is "
     "the governor's. The advisor never writes — it only enriches a proposal before the governor sees it.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Step</th><th>Op</th><th>Subject</th><th>Confidence</th><th>Action</th><th>Governor</th><th>Violated check</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map journal-row journal)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (append-only)</h2>\n"
     "    <p class=\"muted\">The store's own log. Only the graph's <code>:commit</code> node writes to it, so holds and "
     "escalations are absent by design — " (count ledger) " of " (count journal) " runs committed.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Advisor reasoning</th><th>Decision</th><th>Approver</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <p class=\"muted\"><strong>Approver attribution.</strong> " (approver-note journal probe) "</p>\n"
     "    <p class=\"muted\"><strong>Store retention.</strong> " (retention-note probe) "</p>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-952 — repair-shop administrative coordination actor. Administrative logistics only: this actor "
     "never makes a diagnostic or repair-technique decision, never determines warranty or liability, never approves pricing "
     "or a quote, and never overrides safety authority.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store journal] :as run} (run-demo!)
        holds (filter #(= :governor-hold (:t %)) journal)]
    ;; Build-time invariant, not a convention: a console that shows no
    ;; HARD hold is not evidence that the governor is wired in.
    (when (zero? (count holds))
      (throw (ex-info "render-html: scenario produced 0 :governor-hold records -- refusing to write a console that cannot demonstrate the governor"
                      {:journal-size (count journal)
                       :actions (frequencies (map :action journal))})))
    (spit out (render run))
    (println "wrote" out
             (format "(%d graph runs, %d HARD holds across %d distinct checks, %d ledger commits)"
                     (count journal)
                     (count holds)
                     (count (distinct (map :check/id (mapcat :violations holds))))
                     (count (store/ledger store))))))
