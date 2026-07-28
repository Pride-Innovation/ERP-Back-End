# Movement Workflow Documentation

## Overview

A **movement** is the record of stock or an asset physically going from one place to another. It is
the only mechanism that relocates anything between stores, and the only one that changes an asset's
custody. This document covers the model, the lifecycle, and — the important part — **exactly what is
updated at each step**.

Two ideas run through everything below:

1. **Effects are applied on completion, never at initiation.** Creating a movement reserves nothing
   and moves nothing. Stock leaves the source store when a courier actually takes custody, and
   arrives at the destination when it is received. In-transit stock is therefore never double-counted
   — it belongs to the courier, not to either end.
2. **Every effect flows through `InventoryService`**, the single authority over balances. Nothing
   else is permitted to add or remove stock.

---

## The Model

| Entity | Table | What it is |
|---|---|---|
| `Movement` | `movement` | The header: type, category, status, source, destination, logistics, approval state. |
| `MovementItem` | `movement_item` | One line — **either** an asset **or** a commodity with a quantity, never both. |
| `MovementApprovalRecord` | `movement_approval_record` | One row per approval decision, forming the audit trail. |

### Source and destination

A movement has **exactly one source**: a store, or a user handing something back
(`RETURN_TO_STORE`). It needs **at least one destination**: a store, a recipient user, or both.

### Category — derived, never set by hand

`INTER_LOCATION` when the source and destination locations differ, otherwise `INTRA_LOCATION`. For a
return the source location is the handing-back user's branch; for a user destination it is the
recipient's branch.

The category decides the whole lifecycle: inter-location movements must be dispatched, marked in
transit and received; intra-location movements complete immediately.

---

## The Eight Movement Types

| Type | From → To | Typical trigger |
|---|---|---|
| `REPLENISHMENT` | HO Admin store → branch Admin store | Restocking a branch |
| `ISSUANCE_FULFILLMENT` | Source store → user or store | Fulfilling an approved request |
| `DEPARTMENT_TRANSFER` | Store → department store or user | Reallocating within a location |
| `REPAIR_TRANSFER` | Holder's branch Admin store → **HO IT store** | A faulty asset goes for repair |
| `TEMP_REPLACEMENT` | Pool store (HO IT or HO Admin) → user | Lending a stand-in during a repair |
| `RETURN_AFTER_REPAIR` | HO IT/Admin store → originating branch or user | Sending a repaired asset back |
| `DISPOSAL_TRANSFER` | Current store → **HO Disposal store** | Retiring an asset |
| `RETURN_TO_STORE` | **A user** → a store | Someone hands an item back |

### Why IT and Disposal are singletons

There is exactly **one IT store and one Disposal store for the whole organisation**, both at Head
Office. That singleton *is* the control that makes "repair only happens at Head Office" true.

`StoreSeeder` creates them against the branch flagged `isHeadOffice`, refuses to create a second if
the flag is ever moved, and **fails startup if duplicates exist** — a duplicate would let repairs
route to an arbitrary store silently, which is worse than not running. Zero logs a loud error and
lets the application boot, since repair and disposal then fail with an actionable message.

---

## Lifecycle

```
                 ┌──────────── requiresApproval ────────────┐
                 │                                          │
  createMovement ┤                                          ▼
                 │                                    DRAFT ──approve──► INITIATED
                 └── no approval ──► INITIATED                 │
                                          │                 reject
                                          │                    ▼
                                          │                CANCELLED
                                          ▼
   INTER_LOCATION:  INITIATED ──dispatch──► DISPATCHED ──in-transit──► IN_TRANSIT ──receive──► COMPLETED
   INTRA_LOCATION:  INITIATED ─────────────────── complete / receive ──────────────────────► COMPLETED
```

### Where stock and assets actually change hands

This is the table to read if you read nothing else.

| Step | Consumables | Assets | Guards |
|---|---|---|---|
| **create** | nothing | nothing | Exactly one source; a destination or recipient required |
| **approve / reject** | nothing | nothing | Walks the initiator's reporting ladder |
| **dispatch** | nothing | nothing | Tracking number, plate number **and** a signed dispatch document all required |
| **in-transit** | **debit source, credit courier custody store** | `currentStore` → courier store; status → *In Transit* (prior status snapshotted) | Courier must be assigned; guarded by `custodyTransferSettled` so it applies once |
| **receive / complete** | **debit courier (or source), credit destination** | Per type — see below; *In Transit* reverts to the snapshotted status | Inter-location movements **must** have been marked in transit; guarded by `inventorySettled` |
| **cancel** | nothing | nothing | Cannot cancel a completed movement; issued request assets return to the pool |

Both ledger hops are idempotency-guarded, so a repeated call cannot move the same stock twice.

### Asset effects at completion, by type

| Type | Effect |
|---|---|
| `DISPOSAL_TRANSFER` | Relocated to the Disposal store, marked `disposed` with a disposal date |
| `TEMP_REPLACEMENT` | Assigned to the recipient as a **temporary** assignment |
| `REPAIR_TRANSFER` | Returned to the destination store; **the holder's assignment is closed** |
| `RETURN_AFTER_REPAIR` | Reassigned to the recipient, or returned to the store and unassigned |
| `RETURN_TO_STORE` | Returned to the store; the holder's assignment is closed |
| others | Assigned to the recipient, or returned to the destination store |

> Returning an asset to a store always **closes its assignment**. Merely relocating it would leave
> the asset reading as both "in the Admin store" and "assigned to Jane" at once.

---

## The Repair Round-Trip

The most involved flow, and the one with the most business rules.

### 1 — A branch user's laptop breaks

`POST /api/v1/movements/repair-transfer` with `{ "assetId": 1 }`.

The service resolves the source as the asset's current store, falling back to **the Admin store of
the branch that holds it** — which is precisely "the branch admin receives it". The destination
follows the asset category's `RepairDestination`:

| Routing | Destination | Notes |
|---|---|---|
| `IT` (default) | HO IT store | |
| `ADMIN` | HO Admin store | e.g. furniture |
| `EXTERNAL` | HO Admin store, as custody | A consultant **and** dispatch documents are mandatory |

A category marked non-repairable is diverted straight to disposal instead.

**At initiation, three things happen immediately:**

1. The holder and their branch are recorded on the movement as `originHolder` / `originLocation`.
2. The holder's assignment is **closed** and the asset is booked into the branch Admin store.
3. The asset's status becomes *In Maintenance*.

Step 1 exists because of step 2. Once the assignment is closed the system no longer knows whose
laptop it is, so the return destination has to be captured first — otherwise the asset comes back
months later and someone has to remember. Repair transfers also require approval before dispatch.

### 2 — It travels

Branch → Head Office is inter-location, so it must be dispatched (tracking number, plate number,
signed document), marked in transit (custody passes to the courier, asset stamped *In Transit*) and
received at Head Office.

### 3 — It comes back

`POST /api/v1/movements/return-after-repair` with `{ "assetId": 1 }` — **no destination needed**. It
is derived, in this order:

1. the `originHolder` recorded on the repair transfer — the authoritative answer;
2. failing that, the asset's own `assignedTo`, which is how repairs behaved before origins were
   recorded, so anything already in flight still returns correctly with no migration;
3. only then an explicit override, which requires an `overrideReason`.

**Where it goes depends on the holder:**

| Holder's state | Outcome |
|---|---|
| Still employed | Follows them to their **current** branch — including one they have transferred to |
| Left the organisation (`isEnabled = false`) | Returns to the **original** branch, unassigned, and is flagged **available for temporary issuance** so it can be lent out rather than sit idle |

A user is "removed from the organisation" via `POST /api/v1/users/{id}/disable`. Being *blocked* is a
security lock, not a departure, and deliberately does not trigger this.

Any temporary replacement is returned in a second movement that inherits the same courier and
documents as the main return — created bare, an inter-location return would strand in `INITIATED`
because nobody knew it still needed dispatching.

---

## Approval

Opt-in per movement via `requiresApproval`. `REPAIR_TRANSFER` and `RETURN_AFTER_REPAIR` set it;
request-fulfilment movements do not, because the underlying request was already approved.

An approving movement is parked in `DRAFT` and walks the initiator's reporting ladder tier by tier,
reaching `INITIATED` when the last tier clears. If no approver resolves, it is auto-initiated rather
than stranded. Rejection cancels it.

---

## Endpoints

### Core

| Method | URL | Purpose |
|---|---|---|
| `POST` | `/api/v1/movements` | Create a movement |
| `GET` | `/api/v1/movements` | List (paged) |
| `GET` | `/api/v1/movements/{id}` | One movement |
| `GET` | `/api/v1/movements/by-request/{requestId}` | Movements fulfilling a request |
| `POST` | `/api/v1/movements/{id}/dispatch` | Assign courier, record logistics |
| `POST` | `/api/v1/movements/{id}/in-transit` | Custody passes to the courier |
| `POST` | `/api/v1/movements/{id}/receive` | Receive at destination and complete |
| `POST` | `/api/v1/movements/{id}/complete` | Complete an intra-location movement |
| `POST` | `/api/v1/movements/{id}/cancel` | Cancel; applies nothing |

> `/complete` carries the same in-transit precondition as `/receive`. Without it, that endpoint was a
> way around the custody chain — an inter-location movement could be completed with no courier, plate
> number, tracking number or signed note, and its assets never stamped *In Transit*. The ledger would
> still balance, but the custody trail would be fiction.

### Asset lifecycle

| Method | URL | Purpose |
|---|---|---|
| `POST` | `/api/v1/movements/repair-transfer` | Send a faulty asset for repair |
| `POST` | `/api/v1/movements/temp-replacement` | Lend a pool asset |
| `POST` | `/api/v1/movements/return-after-repair` | Send a repaired asset back |
| `POST` | `/api/v1/movements/disposal` | Retire an asset |

### Approval

| Method | URL | Purpose |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/approve` | Approve the current tier |
| `POST` | `/api/v1/movements/{id}/reject` | Reject and cancel |
| `GET` | `/api/v1/movements/pending-approval/{approverId}` | An approver's queue |
| `GET` | `/api/v1/movements/{id}/approvals` | Decision trail |

### Documents

| Method | URL | Purpose |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/documents` | Attach to an existing movement |
| `POST` | `/api/v1/movements/documents` | Upload before creation, for flows that need documents at creation time |

---

## Creating a Movement

```json
{
  "movementType": "REPLENISHMENT",
  "sourceStoreId": 1,
  "destStoreId": 4,
  "recipientUserId": null,
  "requiresApproval": true,
  "remarks": "Monthly branch replenishment",
  "items": [
    { "commodityId": 14, "quantity": 10 },
    { "assetId": 7 }
  ]
}
```

| Field | Required | Notes |
|---|---|---|
| `movementType` | yes | |
| `sourceStoreId` / `sourceUserId` | **exactly one** | `sourceUserId` only for `RETURN_TO_STORE` |
| `destStoreId` / `recipientUserId` | at least one | A return must name a destination store |
| `items[]` | yes | Each carries **either** `assetId` **or** `commodityId` + `quantity` |
| `requiresApproval` | no | Opt-in |
| `originHolderId` / `originLocationId` | — | Set by the system on repair transfers; not for clients |

---

## Known Gaps

- **Request visibility is not branch-scoped** — deliberately, pending a decision.

### Closed since first writing

- ~~Deleting a stock does not reverse store balances~~ — it now does, as a reason-coded
  `STOCK_REVERSAL` adjustment, and is refused outright if the goods have already been issued out.
- ~~No per-branch authorisation~~ — reads are scoped by `VIEW_ALL_BRANCHES`; see "Visibility" below.
- ~~Courier stores borrow Head Office's location~~ — a courier custody store now has **no** location,
  and an in-transit asset keeps the branch it left.
- ~~Money stored as `int`~~ — all money is `DECIMAL(19,4)`.
- ~~No full movement ledger~~ — both custody hops now write `stock_ledger_entry` rows
  (`TRANSFER_OUT` / `CUSTODY_OUT` on dispatch, `CUSTODY_IN` / `TRANSFER_IN` on receipt), each
  pointing back at the movement. See `inventory-stocking-workflow.md`.

## Visibility

Movements are scoped by branch unless the caller holds `VIEW_ALL_BRANCHES`. A branch user sees any
movement touching their branch **at either end** — outbound and inbound — resolved from the source
store, destination store, source user or recipient. Grant the permission to Head Office staff who
need a national view; its absence is the default.

## Related

- `inventory-stocking-workflow.md` — how stock enters the system
- `stock-take-workflow.md` — how balances are reconciled to the shelf
- `stock-take-api.md` — stock-take endpoint reference
