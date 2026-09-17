# Inventory & Stocking Workflow Documentation

## Overview

This is how stock **enters** the system: a purchase order is raised, goods arrive — often in several
batches — each delivery is receipted, a GRN is issued, the store is credited and assets are
registered. This document covers the model, the workflow, and **exactly what is updated by which
action**.

The governing rule:

> **Receiving is the only way quantities enter the system, and everything a receipt touches moves
> together in one transaction** — the delivered quantity, the store balance, the asset register, the
> GRN and the order's status.

That atomicity is the point. It used to be possible to raise a line's delivered figure through the
correction form on its own: the screen said the goods had arrived while the store balance, the asset
register and the GRN trail all still reflected the earlier delivery, and the gap never closed.

---

## The Model

| Entity | Table | What it is |
|---|---|---|
| `Stock` | `stock` | The purchase order header: supplier, LPO/PO numbers, business dates, status. |
| `StockCommodity` | `stock_commodity` | One order line: ordered, delivered, unit prices, assets registered. |
| `StockReceipt` | `stock_receipt` | **One physical delivery.** Its own date, delivery note, invoice, receiving officer, destination store. |
| `GrnReport` | `grn_report` | The Goods Received Note for one receipt. One receipt = one GRN. |
| `GrnCommodity` | `grn_commodity` | One line on that GRN. |
| `StoreBalance` | `store` | On-hand quantity of a commodity in a store. |
| `Asset` | `asset` | A serialized item registered from a delivered unit. |

### Why receipts exist

A purchase order is usually delivered in batches, and a batch previously had nowhere to live. The
delivery date sat on the mutable stock header, so a later batch silently rewrote the depreciation
start date of an earlier one; each commodity received on its own produced a separate GRN; and the
supplier's delivery-note reference was not captured at all.

A receipt gives one physical delivery one row — **one delivery note becomes one GRN**, covering every
line on that delivery rather than one GRN per commodity.

---

## The Workflow

```
  create order ──► receive batch 1 ──► receive batch 2 ──► … ──► COMPLETED
   (stockPending)      (GRN #1)            (GRN #2)
                                                        └──► or CLOSED SHORT
                                                             (balance not expected)

  correct order details ──► (any time; changes no quantities received)
```

### 1 — Create the order

`POST /api/v1/stocks`. Anything already delivered at creation is itself a receipt and goes through
exactly the same path as a later batch. An order captured with **nothing** delivered yet produces no
GRN at all — a "zero delivery" GRN is a meaningless document in the audit trail.

Creation carries an **idempotency key**: a retried or double-submitted request resolves to the stock
already created rather than duplicating it and its assets.

### 2 — Receive a delivery

`POST /api/v1/stocks/{stockID}/receipts`. Quantities are **deltas** — what arrived in this batch,
never the new cumulative total.

```json
{
  "lines": [
    { "commodityId": 1, "deliveredQuantity": 6, "costPrice": 1100, "purchasePrice": 1300 }
  ],
  "receiptDate": "2026-07-25T00:00:00",
  "deliveryNoteNumber": "DN-04821",
  "invoiceNumber": "INV-2291",
  "notes": "Second batch, all in good condition",
  "idempotencyKey": "a4f1…"
}
```

**What one receipt updates, atomically:**

| # | Updated | Detail |
|---|---|---|
| 1 | `StockCommodity.deliveredQuantity` | Increased by the delta; rejected if it would exceed ordered |
| 2 | `StockReceipt` | Created — date, delivery note, invoice, receiving officer, destination store |
| 3 | `GrnReport` + `GrnCommodity` | One GRN for the receipt, one line per commodity received |
| 4 | `StoreBalance` | The branch's ADMIN store credited, via `InventoryService` |
| 5 | `Asset` | One asset per newly delivered unit, for asset-tracking categories only |
| 6 | `Stock.status` | Recomputed from the lines |

**Line-level detail worth knowing:**

- **Unit prices** may differ per batch. The line's price is updated so assets created from *this*
  batch carry *this* batch's price; assets already registered keep the price they were stamped with.
- **Ordered quantity on the GRN** is taken from the order, not from the request. A client echoing back
  the wrong figure used to make the GRN misstate the order.
- **Zero-quantity lines are ignored**, so a form submitted with most rows blank records only what
  actually arrived instead of a GRN padded with empty entries.
- **Assets are stamped with the receipt's own date** — not the stock header. This is the basis for
  each asset's age and depreciation, and a later batch can no longer rewrite an earlier one's.
- **Idempotency**: replaying the same key returns the original receipt unchanged. A double-click
  cannot receive the batch twice.

### 3 — Status, derived

Status is computed from the lines, never taken from the client:

| Condition | Status |
|---|---|
| `sum(delivered) == sum(ordered)` | `stockCompleted` |
| Otherwise, and previously closed short | stays `stockClosedShort` |
| Otherwise | `stockPending` |

### 4 — Close short

`POST /api/v1/stocks/{stockID}/close-short`. The outstanding balance is accepted as never arriving,
so the order leaves `stockPending` for a terminal state instead of sitting open indefinitely.
Already-received units and their assets are untouched.

### 5 — Correct the order

`PUT /api/v1/stocks/{stockID}` fixes the header (supplier, references, dates), **ordered** quantities
and unit prices.

**It cannot change delivered quantities.** Attempting to do so is rejected:

> *Delivered quantity for Chair cannot be changed here (currently 5). Record a delivery against this
> order instead, so the store, the asset register and the GRN are updated with it.*

Other correction rules:

- Ordered cannot be set below what has already been received.
- A line added by correction starts at **zero** delivered, whatever was submitted — nothing has been
  received against it yet.
- A line can only be removed when **nothing has been received** against it. (The test used to be
  "no assets registered", which consumables never have — so a stationery line could be deleted while
  its units stayed on the store balance for good.)
- Status is recomputed afterwards, so correcting an order down to what actually arrived completes it
  rather than leaving it stuck on pending.

---

## Concurrency and Reliability

| Concern | Handling |
|---|---|
| Two storekeepers receiving at once | `@Version` optimistic locking on `StockCommodity` and `StoreBalance`; a lost race returns `409` with a plain-language message |
| Double submission | Idempotency keys on both order creation and receiving |
| Email failure | Stocking notifications are published in-transaction but **delivered after commit** — a dead SMTP host cannot roll back a delivery that physically happened |

---

## Reconciliation

`GET /api/v1/stock-reconciliation` cross-checks the three independent records of a received unit that
ought to agree:

| Column | Meaning |
|---|---|
| `deliveredQuantity` | What the order line says was received |
| `grnCoveredQuantity` | What the GRN trail can account for |
| `assetsOnRegister` | Assets actually registered for that stock + commodity |
| `quantityMissingFromGrn` | Delivered units with no receipt behind them — **never credited to a store** |
| `assetsMissing` | Received units of an asset-tracked category never registered |

It is **read-only by design**. What to do about a discrepancy — write it off, receive it properly, or
correct the order — is a business decision, and silently generating adjustments to paper over it
would be exactly the kind of invisible change that caused the problem.

`?onlyDiscrepancies=false` returns every line, for export.

---

## Endpoints

### Orders and receiving

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/v1/stocks` | List orders (filters: `lpoNumber`, `stockStatusId`, date range) |
| `POST` | `/api/v1/stocks` | Create an order |
| `GET` | `/api/v1/stocks/{id}` | One order with its lines and GRNs |
| `PUT` | `/api/v1/stocks/{id}` | **Correct** the order — quantities received are not editable |
| `POST` | `/api/v1/stocks/{id}/receipts` | **Record a delivery** |
| `GET` | `/api/v1/stocks/{id}/receipts` | Delivery history, one entry per physical receipt |
| `POST` | `/api/v1/stocks/{id}/close-short` | Accept the outstanding balance as undelivered |
| `POST` | `/api/v1/delete-stock/{id}` | Soft-delete the order, its GRNs, receipts and assets |
| `POST` | `/api/v1/stocks/{id}/complete-delivery` | **Deprecated** — superseded by `/receipts` |

### GRNs

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/v1/grn-commodities/{stockID}` | GRN lines for an order (**paged — pass `size`**) |
| `PUT` | `/api/v1/download-grn/{grnReportId}` | Mark a GRN downloaded |
| `POST` | `/api/v1/upload-grn/{grnReportId}` | Upload the signed GRN |
| `GET` | `/api/v1/grn-reports/{grnReportId}/document` | Fetch the signed document as a blob |

### Stores and balances

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/v1/store?branchId=&assetTypeId=&storeType=` | Balances for a branch, scoped to a store type |
| `GET` | `/api/v1/store/{branchId}` | All non-courier balances for a branch (empty list if none) |
| `GET` | `/api/v1/inventory/balances` | Balances on hand, filterable by location/store/department/type |
| `GET` | `/api/v1/inventory/in-transit` | Stock in a courier's custody — deliberately separate |
| `GET` | `/api/v1/inventory/low-stock` | Balances at or below their reorder threshold |
| `PUT` | `/api/v1/inventory/balances/{id}/min-level` | Set or clear a reorder threshold |
| `GET` | `/api/v1/inventory/branch-overview` | Every branch's position in **one** call |
| `GET` | `/api/v1/inventory/stores` | Stores directory |
| `GET` | `/api/v1/inventory/stores/{storeId}/assets` | Assets held by a store |
| `GET` | `/api/v1/inventory/assets/scoped?branchId=&departmentId=&unitId=` | Assets by position in the organisation |
| `GET` | `/api/v1/inventory/my-items` | What the signed-in user holds |
| `POST` | `/api/v1/stores` | Create a **department-level** store |
| `PUT` | `/api/v1/stores/{id}/active` | Activate / deactivate (refused while it holds stock) |

> **In transit is not on hand.** Courier custody stores are pinned to Head Office by the schema, so
> including them in balance queries both double-counted goods still in a van and attributed every
> in-transit unit in the country to Head Office. `/inventory/balances`, `/inventory/low-stock` and
> the location filter all exclude them; `/inventory/in-transit` shows them on their own.

---

## Consumables vs Assets

Two different things live in a store and are tracked differently:

| | Consumables | Serialized assets |
|---|---|---|
| Tracked as | `StoreBalance` rows (a quantity) | `Asset.currentStore` (a foreign key) |
| Created by | Crediting a balance | One `Asset` row per delivered unit |
| Which categories | `AssetType.tracksAssets = false` | `tracksAssets = true` |
| Counted by | Stock take | Verification sweep (second release) |

This is why the IT and Disposal store pages read from the assets endpoint rather than the balance
table — those stores hold nothing but assets.

---

## Known Gaps


- **No per-branch write authorisation on every path yet** — reads are scoped, and store/stock-take
  writes are gated, but not every mutating endpoint in the wider app carries a permission.
- **Request visibility is not branch-scoped** — deliberately, pending a decision (see below).

### Closed since first writing

- ~~Money stored as `int`~~ — all money is now `DECIMAL(19,4)`. `Asset.purchaseCost` and
  `costOfTheAsset` were free-text `VARCHAR` and are now decimals too. Four decimal places because
  depreciation divides by twelve: 5,000,000 × 33.33% ÷ 12 = 138,875, and the second year gives
  92,587.9625 exactly.
- ~~No full movement ledger~~ — every credit and debit now writes a `stock_ledger_entry` carrying a
  reason and a pointer to the document behind it. See "The Ledger" below.

- ~~Deleting a stock does not reverse store balances~~ — deleting now debits the store and records a
  `STOCK_REVERSAL` adjustment. If the goods have already been issued out the delete is **refused**
  rather than driving the balance negative.
- ~~No per-branch authorisation~~ — read endpoints are scoped by the `VIEW_ALL_BRANCHES` permission.
  Without it a user sees only their own branch; naming another branch explicitly returns `403`.
  Write endpoints now require `CREATE_STORE` / `UPDATE_STORE`.

## The Ledger

`StoreBalance.quantity` is now a **cache** of the ledger rather than the only copy of the truth.
Every credit and debit through `InventoryService` writes one immutable `stock_ledger_entry`:

| Column | Meaning |
|---|---|
| `quantity_delta` | Signed; positive brought stock in |
| `balance_after` | The balance once applied, so a row explains itself |
| `reason` | `RECEIPT`, `ISSUANCE`, `TRANSFER_OUT`/`IN`, `CUSTODY_OUT`/`IN`, `RETURN_TO_STORE`, `STOCK_TAKE`, `STOCK_REVERSAL`, `UNSPECIFIED` |
| `source_type` + `source_id` | The document that caused it — a receipt, movement or count |

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/v1/inventory/ledger` | Entries, newest first, by store and date window |
| `GET` | `/api/v1/inventory/ledger/history?storeId=&commodityId=` | How one line reached its quantity |
| `GET` | `/api/v1/inventory/ledger/verify` | Recompute balances from the ledger and report drift |

A receipt and its later reversal read as:

```
+10 -> balance 10 | RECEIPT        | GRN GRN/2026/000252
-10 -> balance  0 | STOCK_REVERSAL | Reversal of deleted stock MON-LPO-1
```

**Balances that predate the ledger show as drift on `verify`.** That is accurate, not a fault — it is
the historical position the ledger genuinely cannot account for. No backfill was attempted, because
inventing entries to make the numbers agree would be fabricating an audit trail.

`UNSPECIFIED` means a code path changed stock without saying why. It is logged as a warning and
should be treated as a defect to fix, not a normal state.

## Related

- `movement-workflow.md` — how stock moves between stores once it is in
- `stock-take-workflow.md` — how balances are reconciled to the shelf
- `stock-take-api.md` — stock-take endpoint reference
