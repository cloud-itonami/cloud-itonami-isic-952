(ns repairshop.phase-test
  (:require [clojure.test :refer [deftest testing is]]
            [repairshop.phase :as phase]))

(deftest phase-0-is-read-only
  (is (false? (phase/allowed-in-phase? 0 :schedule-repair-intake))))

(deftest phase-1-allows-intake-not-staff-shift
  (is (true? (phase/allowed-in-phase? 1 :schedule-repair-intake)))
  (is (false? (phase/allowed-in-phase? 1 :schedule-staff-shift-proposal))))

(deftest phase-2-adds-staff-shift
  (is (true? (phase/allowed-in-phase? 2 :schedule-staff-shift-proposal))))

(deftest phase-3-allows-everything
  (is (true? (phase/allowed-in-phase? 3 :flag-safety-concern))))

(deftest phase-3-auto-commits-safe-ops
  (is (true? (phase/auto-commits-in-phase? 3 :coordinate-repair-status-update))))

(deftest safety-concern-never-auto-commits
  (testing ":flag-safety-concern is never in a phase's auto-commit set, at any phase"
    (is (false? (phase/auto-commits-in-phase? 1 :flag-safety-concern)))
    (is (false? (phase/auto-commits-in-phase? 2 :flag-safety-concern)))
    (is (false? (phase/auto-commits-in-phase? 3 :flag-safety-concern)))))

(deftest phases-1-and-2-never-auto-commit
  (testing "auto-commit requires phase 3 -- phases 1-2 only ALLOW, never auto-commit"
    (is (false? (phase/auto-commits-in-phase? 1 :schedule-repair-intake)))
    (is (false? (phase/auto-commits-in-phase? 2 :schedule-repair-intake)))))
