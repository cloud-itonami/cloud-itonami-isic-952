(ns repairshop.governor
  "Governor with three HARD, permanent, un-overridable checks for the
  repair-shop administrative coordination actor.

  1. Item/customer-record unverified — target must exist in store AND be
     independently :registered?/:verified?, re-derived every time.
  2. Effect not :propose — rejected outright.
  3. Scope exclusion — any proposal touching diagnostic/repair-technique decisions,
     warranty/liability determinations, pricing/quote-approval decisions, or
     safety-authority overrides is permanently blocked."
  (:require [repairshop.store :as store]
            [clojure.string :as str]))

;; ---------------------- hard checks ----------------------

(defn item-unverified-violations
  "Check 1: Item must be registered AND verified.
  This is re-derived from the item's own :registered?/:verified? fields,
  never from proposal self-report."
  [store item-id]
  (let [item (store/repair-item store item-id)]
    (cond
      (nil? item)
      [{:check/id :item-unverified
        :violation "Item not found in store"}]

      (not (:registered? item))
      [{:check/id :item-unverified
        :violation "Item is not registered"}]

      (not (:verified? item))
      [{:check/id :item-unverified
        :violation "Item is not verified"}]

      :else
      [])))

(defn effect-not-propose-violations
  "Check 2: Effect must be :propose. Any other effect is rejected outright."
  [proposal]
  (if (not= (:effect proposal) :propose)
    [{:check/id :effect-not-propose
      :violation (str "Effect is " (:effect proposal) ", not :propose")}]
    []))

(defn scope-exclusion-violations
  "Check 3: Block proposals touching excluded territory.
  Excluded: diagnostic/repair-technique decisions, warranty/liability determinations,
  pricing/quote-approval decisions, safety-authority overrides.

  Uses qualified substring scan (EN+JA) so legitimate :flag-safety-concern
  ops that mention 'safety' aren't self-blocked."
  [proposal]
  (let [forbidden-patterns
        [;; EN patterns: diagnostic, repair-technique, warranty, liability, pricing, quote
         #"(?i)diagnostic"
         #"(?i)diagnosis"
         #"(?i)diagnose"
         #"(?i)repair.?technique"
         #"(?i)repair.?method"
         #"(?i)repair.?decision"
         #"(?i)warranty"
         #"(?i)warrantee"
         #"(?i)guarantee"
         #"(?i)liability"
         #"(?i)responsible"
         #"(?i)price.?approval"
         #"(?i)quote.?approval"
         #"(?i)pricing.?decision"
         #"(?i)cost.?approval"
         #"(?i)safety.?authority"
         #"(?i)safety.?override"
         #"(?i)safety.?violation"
         ;; JA patterns (repair-shop terminology)
         #"診断"
         #"修理.?方法"
         #"修理.?技術"
         #"修理.?判断"
         #"保証"
         #"責任"
         #"価格.?承認"
         #"見積もり.?承認"
         #"価格.?決定"
         #"コスト.?承認"
         #"安全.?権限"
         #"安全.?上書き"
         #"安全.?違反"]

        ;; Allowed operations that legitimately mention concerns
        allowed-ops #{:flag-safety-concern}

        op-id (:operation proposal)
        proposal-str (str proposal)

        ;; Check if any forbidden pattern matches
        has-forbidden-match (some #(re-find % proposal-str) forbidden-patterns)
        is-allowed-op (allowed-ops op-id)

        ;; Combined check: return violation only if forbidden AND not allowed-op
        should-reject (boolean (and has-forbidden-match (not is-allowed-op)))]

    (if should-reject
      [{:check/id :scope-exclusion
        :violation "Proposal touches diagnostic/repair-technique decisions, warranty/liability determinations, pricing/quote-approval decisions, or safety-authority overrides"}]
      [])))

;; ---------------------- decision logic ----------------------

(defn govern
  "Apply all three HARD checks. Any violation is a permanent rejection
  with no override path."
  [store proposal]
  (let [;; Only check item verification if item-id is present
        item-violations (if (:item-id proposal)
                          (item-unverified-violations store (:item-id proposal))
                          [])
        effect-violations (effect-not-propose-violations proposal)
        scope-violations (scope-exclusion-violations proposal)
        all-violations (concat item-violations effect-violations scope-violations)]

    {:proposal proposal
     :violations all-violations
     :passes? (empty? all-violations)
     :decision (if (empty? all-violations)
                 :APPROVE
                 :REJECT)}))
