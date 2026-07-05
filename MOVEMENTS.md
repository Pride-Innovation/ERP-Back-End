# Movements & Store Updates — UI Walkthrough

A short guide to how every movement type flows through the system, what it does to the
stores, and where each step happens in the UI. Companion to `workflow.md` (which covers
request approval); this covers everything after "the items need to physically move".

---

## 1. The building blocks

**Stores** (Store page → Admin / IT / Disposal tabs):

| Store type | Who has one | Holds |
|---|---|---|
| `ADMIN` | Every branch (auto-created on first use) | Main inventory: consumable balances + issuable assets |
| `IT` | Head Office only | Assets under repair, temporary replacements |
| `DISPOSAL` | Head Office only | Written-off / irreparable assets |

**Two kinds of movement lines:**
- **Commodity line** (consumables, e.g. stationery) — moves a *quantity*; updates `StoreBalance` rows.
- **Asset line** (serialized, e.g. a laptop) — moves *one specific asset*; updates the asset's
  `currentStore` / `assignedTo` / branch, never a quantity.

**The golden rule:** store balances change **only when a movement completes** (is received),
never at creation or dispatch — so in-transit stock is never double-counted. The
`inventorySettled` flag guarantees a movement's effects apply exactly once.

**Category is derived, never chosen:**
- Source and destination in **different locations** → `INTER_LOCATION` (full dispatch → receive ladder, courier required).
- Same location → `INTRA_LOCATION` (one-step complete, no courier).

---

## 2. The universal lifecycle

```
            (standalone only)
 create ──► DRAFT ──approve──► INITIATED ──dispatch──► DISPATCHED ──► IN_TRANSIT ──receive──► COMPLETED
                └─reject──► CANCELLED                      (inter-location only)      │
                                                                                      ▼
                                            INTRA_LOCATION: INITIATED ──complete──► COMPLETED
                                                                        (ledger applies here)
```

| Step | Who | UI |
|---|---|---|
| Create | Any officer | Movement → **New Movement**, or **Repair / Disposal** modal |
| Approve / Reject (DRAFT) | Each tier of the *initiator's* reporting ladder (Supervisor → Manager → HoD, or Supervisor → BOM → Branch Manager) | Movement page → **Pending My Approval** inbox, or the movement detail view |
| Dispatch | Source store officer (courier + tracking # required) | Movement detail / list → **Dispatch**. Print the **Release Note / Gate Pass** PDF here, get it signed, upload the scan under Documents |
| In transit | Optional marker | Movement detail / list |
| Receive | Receiving officer / BOM at destination — records `RECEIVED_OK` or `RECEIVED_WITH_DISCREPANCY` + remarks | Movement detail / list → **Receive** |
| Complete (intra) | Destination officer | Movement detail / list → **Complete** |

**Approval applies only to standalone movements** (`requiresApproval = true`, e.g. repair
transfers). Movements auto-created to fulfil an **already-approved request** skip it — the
request was approved by its own workflow.

---

## 3. Each movement type, start to finish

### 3.1 ISSUANCE_FULFILLMENT — approved request fulfilled from another location
*The only movement the system creates by itself (Phase: after issuance approval).*

1. HO admin issues items against an approved request (Requests → Issue) and the issuer's
   manager approves the issuance.
2. Same location as requester → **no movement** (direct issuance; HO ADMIN store debited immediately).
3. Different location → the system auto-creates an INITIATED movement: **HO ADMIN store → requester-branch ADMIN store**, linked to the request. No approval needed.
4. HO dispatches (courier, tracking, release note) → branch receives.
5. **On receive:** HO ADMIN balance ↓, branch ADMIN balance ↑, and the system auto-creates
   the **second leg**: an intra-branch `DEPARTMENT_TRANSFER` from the branch store to the
   requester. The BOM just completes it → balance ↓, items with the requester.
6. Requester acknowledges receipt (Requests page) — request completed.

### 3.2 REPLENISHMENT — branch restocks from Head Office
Same pipeline as 3.1; the system labels it REPLENISHMENT when the requester is the branch's
**BOM** (stock for the branch, not a person). On receive: HO ↓, branch ADMIN ↑, done — no
second leg to a person.

### 3.3 DEPARTMENT_TRANSFER — reallocate within a location
1. New Movement → type Department Transfer, source store, destination store **or recipient user**; tick *Requires approval* if policy demands it.
2. Intra-location → just **Complete**. Commodities: source ↓ (+ destination ↑ if store-to-store; debit-only if to a user). Assets: relocated or assigned to the user.

### 3.4 REPAIR_TRANSFER — faulty asset goes for repair
*Movement page → Repair / Disposal modal → Repair Transfer. Always requires approval (initiator's ladder).*

1. Pick the faulty asset (by engraved number). Routing comes from its **category** (Settings → Asset Categories → Repair Routing), overridable:
   - **Non-repairable category** → auto-diverted to a Disposal movement (§3.7). A warning shows in the modal.
   - **IT** → destination = HO **IT store**.
   - **ADMIN** → destination = HO **ADMIN store** (facilities team).
   - **EXTERNAL** → custody = HO ADMIN store + a **consultant is mandatory** (Settings → Repair Consultants) + at least one **signed dispatch document** must be attached in the modal.
2. Asset status flips to **In Maintenance** immediately.
3. Approval ladder → dispatch → receive. **On receive:** asset relocates into the IT/ADMIN store.
4. Optionally issue a temp replacement in the same operation (or via §3.5).

### 3.5 TEMP_REPLACEMENT — loaner while the repair runs
1. Repair / Disposal modal → Temporary Replacement: pick a spare asset **from the IT store** and the recipient user.
2. On completion the asset is assigned to the user as a **temporary** assignment (tracked in assignment history), leaves the IT store.

### 3.6 RETURN_AFTER_REPAIR — repaired asset goes home
1. Repair / Disposal modal → Return After Repair: pick the repaired asset (source = wherever it currently sits: IT, Admin, or external custody), destination location, optionally the user to re-assign to, optionally the temp asset to pull back. Requires approval.
2. On receive: asset re-assigned to its user (or into the destination ADMIN store); the temp asset (if any) returns to the IT store via its own movement.

### 3.7 DISPOSAL_TRANSFER — write-off
1. Repair / Disposal modal → Disposal (or auto-divert from §3.4). Destination is always the HO **Disposal store**.
2. Asset status → **Pending Disposal** immediately.
3. On completion: asset relocates to the Disposal store and is flagged **disposed** with a disposal date. It can no longer be issued.
   *(Tip: assets past their category's useful life show "Ready for disposal" on the Assets pages.)*

---

## 4. Store-update cheat sheet (what completion does)

| Movement type | Commodity lines | Asset lines |
|---|---|---|
| ISSUANCE_FULFILLMENT (leg 1) | source ↓, dest store ↑ | relocate to dest store |
| ISSUANCE_FULFILLMENT / DEPT_TRANSFER → **user** | source ↓ only (consumed) | assign to user, leave store |
| REPLENISHMENT | source ↓, dest ↑ | relocate |
| DEPARTMENT_TRANSFER (store→store) | source ↓, dest ↑ | relocate |
| REPAIR_TRANSFER | — | relocate into IT/ADMIN store |
| TEMP_REPLACEMENT | — | **temporary** assignment to user |
| RETURN_AFTER_REPAIR | — | re-assign to user / relocate to dest store |
| DISPOSAL_TRANSFER | — | relocate to Disposal store + mark disposed |
| CANCELLED (any) | nothing | nothing |

Check the results live on the **Store pages** (Admin / IT / Disposal): consumable balances
(with per-commodity low-stock thresholds) and the assets each store currently holds.

---

## 5. Quick UI test script

1. **Config once:** Settings → Asset Categories (repair routing per category), Settings → Repair Consultants (≥1 active vendor).
2. **Cross-location fulfilment:** approve a branch request → issue from HO → approve issuance → movement appears (Movement → All) → dispatch (print + upload release note) → receive at branch (try a discrepancy) → complete the auto delivery leg → confirm HO ↓ / branch ↑ on the Store pages.
3. **Repair round-trip:** Repair Transfer (IT-routed) → approve it from **Pending My Approval** → dispatch → receive (asset now in IT store) → Temp Replacement to the user → Return After Repair (+ temp back) → verify the IT store and the user's assets.
4. **External repair:** set a category to EXTERNAL → Repair Transfer now demands consultant + signed document.
5. **Disposal:** dispose an asset (or repair-transfer a non-repairable one) → verify it lands in the Disposal store as disposed.
