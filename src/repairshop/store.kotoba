(ns repairshop.store
  "MemStore for repair-shop administrative coordination.
  Contains: customer directory, repair-item directory, staff directory,
  supplies ledger, and append-only transaction log.")

;; ---------------------- protocol ----------------------

(defprotocol MemStore
  (customer [store customer-id])
  (all-customers [store])
  (repair-item [store item-id])
  (all-items [store])
  (staff [store staff-id])
  (all-staff [store])
  (supply [store supply-id])
  (all-supplies [store])
  (append-log [store entry])
  (ledger [store] "the append-only transaction log, oldest first"))

;; ---------------------- in-memory implementation ----------------------

(deftype InMemoryStore
  [customers-atom items-atom staff-atom supplies-atom log-atom]

  MemStore

  (customer [store customer-id]
    (get @customers-atom customer-id))

  (all-customers [store]
    (vals @customers-atom))

  (repair-item [store item-id]
    (get @items-atom item-id))

  (all-items [store]
    (vals @items-atom))

  (staff [store staff-id]
    (get @staff-atom staff-id))

  (all-staff [store]
    (vals @staff-atom))

  (supply [store supply-id]
    (get @supplies-atom supply-id))

  (all-supplies [store]
    (vals @supplies-atom))

  (append-log [store entry]
    (swap! log-atom conj (assoc entry :timestamp #?(:clj (System/currentTimeMillis) :cljs (js/Date.now)))))

  (ledger [store] @log-atom))

;; ---------------------- constructor & demo data ----------------------

(defn create-store
  "Create a new in-memory store with demo data:
  - 3 customers (verified, registered)
  - 5 repair items in various states
  - 2 staff members
  - 3 supply items
  - append-only ledger (empty initially)"
  []
  (let [customers-atom (atom {
    :cust-001 {:id :cust-001
               :name "Alice Johnson"
               :phone "555-1001"
               :registered? true
               :verified? true
               :address "123 Main St"}
    :cust-002 {:id :cust-002
               :name "Bob Smith"
               :phone "555-1002"
               :registered? true
               :verified? true
               :address "456 Oak Ave"}
    :cust-003 {:id :cust-003
               :name "Carol Davis"
               :phone "555-1003"
               :registered? false
               :verified? false
               :address "789 Elm Dr"}})

        items-atom (atom {
    :item-001 {:id :item-001
               :customer-id :cust-001
               :description "Leather shoes - heel replacement"
               :intake-date "2026-07-14"
               :status :awaiting-diagnosis
               :registered? true
               :verified? true}
    :item-002 {:id :item-002
               :customer-id :cust-002
               :description "Wristwatch - battery replacement"
               :intake-date "2026-07-13"
               :status :in-progress
               :registered? true
               :verified? true}
    :item-003 {:id :item-003
               :customer-id :cust-001
               :description "Dining chair - leg repair"
               :intake-date "2026-07-12"
               :status :completed
               :registered? true
               :verified? true}
    :item-004 {:id :item-004
               :customer-id :cust-003
               :description "Microwave oven - unknown issue"
               :intake-date "2026-07-15"
               :status :intake-scheduled
               :registered? false
               :verified? false}
    :item-005 {:id :item-005
               :customer-id :cust-002
               :description "Jewelry pendant - clasp repair"
               :intake-date "2026-07-11"
               :status :ready-for-pickup
               :registered? true
               :verified? true}})

        staff-atom (atom {
    :staff-001 {:id :staff-001
                :name "Tom Wilson"
                :role :technician
                :certified? true}
    :staff-002 {:id :staff-002
                :name "Diane Martinez"
                :role :intake-coordinator
                :certified? false}})

        supplies-atom (atom {
    :sup-001 {:id :sup-001
              :name "Brown shoelaces"
              :category :consumable
              :stock 45}
    :sup-002 {:id :sup-002
              :name "Office packing tape"
              :category :office-supply
              :stock 12}
    :sup-003 {:id :sup-003
              :name "Shop cleaning supplies"
              :category :facility
              :stock 8}})

        log-atom (atom [])]

    (->InMemoryStore customers-atom items-atom staff-atom supplies-atom log-atom)))
