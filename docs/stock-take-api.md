# Stock Take API Documentation

## Overview

A stock take is a physical count of what a store actually holds, followed by a correction of the
system balances to match. This document covers the workflow and every endpoint the frontend needs.

Available endpoints:

- `POST /api/v1/stock-counts` — open a count sheet for a store
- `GET /api/v1/stock-counts` — list counts
- `GET /api/v1/stock-counts/{countId}` — one count with its lines
- `PUT /api/v1/stock-counts/{countId}/lines` — record counted quantities
- `POST /api/v1/stock-counts/{countId}/submit` — hand over for review
- `POST /api/v1/stock-counts/{countId}/approve` — accept the variances
- `POST /api/v1/stock-counts/{countId}/send-back` — return the sheet to be recounted
- `POST /api/v1/stock-counts/{countId}/post` — write the variances to the balances
- `POST /api/v1/stock-counts/{countId}/cancel` — abandon a count
- `GET /api/v1/stock-adjustments` — the variance report

All endpoints require:

- `Authorization: Bearer <jwt-token>`

Scope: **consumables only**. Serialized assets are a second release — an asset is *verified present*
item by item rather than counted, which is a different workflow. See "Not yet covered" at the end.

---

## The Workflow

```
  OPEN ──► COUNT ──► SUBMIT ──► APPROVE ──► POST
  DRAFT   IN_PROGRESS SUBMITTED  APPROVED   POSTED
                          │
                          └── send back ──► IN_PROGRESS

  cancel (any time before POSTED) ──► CANCELLED
```

### Two rules that govern everything

**1. Nothing touches a balance until POST.** Counting, submitting and approving change no stock
whatsoever. An abandoned, cancelled or rejected count leaves the store exactly as it found it. Only
`POST` moves a single unit.

**2. Every posted variance leaves a reason-coded `stock_adjustment` row.** A balance is never
quietly rewritten. Receipts and issues already have documents behind them — a GRN, an issuance
record — so you can always ask why they happened; an adjustment previously had nothing, and the
balance simply became a different number. That is now impossible.

### Step 1 — Open

Opening a sheet **snapshots** what the system currently believes for every commodity the store
holds. Variances are later measured against that snapshot, not against a live figure that may move
while the shelves are being counted.

Only one unfinished count per store is allowed. Two people counting the same shelves at once produce
two conflicting truths, so a second is refused until the first is posted or cancelled.

### Step 2 — Count (blind by default)

**The counter does not see the system quantity while counting.** `systemQuantity` and `variance` come
back as `null` on every line until the sheet is submitted. This is deliberate: showing the expected
figure invites confirming it rather than counting the shelf, and it is the single biggest determinant
of whether a stock take finds problems or rubber-stamps them.

Set `blind: false` when opening only if your process deliberately shows expected figures.

Counting can be partial — call `PUT .../lines` as often as you like with whatever subset you have
counted so far. Only the lines you send are touched.

### Step 3 — Submit

Three checks run, and all must pass:

| Check | Why |
|---|---|
| Every line has been counted | A blank is **not** a zero. Treating one as zero would write off stock nobody actually looked for. |
| Every non-zero variance has a reason | A variance without a reason is just an unexplained number. |
| Reason `OTHER` has a note | Otherwise it explains nothing. |

On submission the system quantities and variances are revealed.

### Step 4 — Approve (or send back)

**The person who counted cannot approve their own count.** Approving records who accepted the
variances — the accountable party — and still changes no stock.

`send-back` returns the sheet to `IN_PROGRESS` with mandatory remarks so the counter knows what to
redo.

### Step 5 — Post

Before anything is written, each line's *current* balance is compared with the snapshot taken when
the sheet was opened.

**If stock moved in between, nothing is posted.** The count is no longer a statement about the
present, and neither available answer is correct: `counted − snapshot` ignores the movement, while
`counted − current` double-counts it. Rather than pick a wrong number, those lines are flagged
`recountRequired`, the sheet is returned to `IN_PROGRESS` with an explanation, and the request fails
with `400`. Recounting a flagged line clears the flag.

When there is no drift, each changed line is applied through the inventory ledger — the same path
every other stock movement uses — and one `stock_adjustment` row is written per changed line.

---

## Endpoints

### Open a Count

- Method: `POST`
- URL: `/api/v1/stock-counts`
- Response: `201` with a `CountView`

```json
{
  "storeId": 1,
  "assetTypeId": null,
  "blind": true,
  "notes": "Month-end count"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `storeId` | number | yes | The store being counted. |
| `assetTypeId` | number | no | Narrows the sheet to one asset category. Omit to count everything the store holds. |
| `blind` | boolean | no | Defaults to `true`. |
| `notes` | string | no | Free text. |

Errors: `400` if the store already has an unfinished count; `404` if the store or category is unknown.

### Record Counted Quantities

- Method: `PUT`
- URL: `/api/v1/stock-counts/{countId}/lines`
- Response: `200` with the updated `CountView`

```json
{
  "lines": [
    { "commodityId": 14, "countedQuantity": 5, "reason": "DAMAGED", "note": "Two chairs broken" },
    { "commodityId": 23, "countedQuantity": 3, "reason": "FOUND",   "note": "Unrecorded return" }
  ]
}
```

Send a `countedQuantity` of `null` to clear a line back to uncounted. Allowed while the count is
`DRAFT` or `IN_PROGRESS`.

### Submit / Approve / Send Back / Post / Cancel

| Action | Method + URL | Body | Allowed from |
|---|---|---|---|
| Submit | `POST /stock-counts/{id}/submit` | none | `DRAFT`, `IN_PROGRESS` |
| Approve | `POST /stock-counts/{id}/approve` | `{ "remarks": "…" }` optional | `SUBMITTED` |
| Send back | `POST /stock-counts/{id}/send-back` | `{ "remarks": "…" }` **required** | `SUBMITTED` |
| Post | `POST /stock-counts/{id}/post` | none | `APPROVED` |
| Cancel | `POST /stock-counts/{id}/cancel` | `{ "remarks": "…" }` optional | anything except `POSTED` |

### Variance Report

- Method: `GET`
- URL: `/api/v1/stock-adjustments`

| Parameter | Type | Description |
|---|---|---|
| `storeId` | number | Optional. Limit to one store. |
| `from` | ISO date-time | Optional. |
| `to` | ISO date-time | Optional. |

```json
{
  "totalLines": 2,
  "unitsWrittenUp": 1,
  "unitsWrittenDown": 2,
  "byReason": [
    { "reason": "DAMAGED", "lines": 1, "netUnits": -2 },
    { "reason": "FOUND",   "lines": 1, "netUnits": 1 }
  ],
  "adjustments": [
    {
      "commodityName": "Chair",
      "quantityBefore": 7,
      "quantityAfter": 5,
      "quantityDelta": -2,
      "reason": "DAMAGED",
      "note": "Two chairs broken beyond repair",
      "countReference": "SC/2026/000001",
      "postedAt": "2026-07-28T22:29:27"
    }
  ]
}
```

---

## Response Shapes

### `CountView`

| Field | Type | Notes |
|---|---|---|
| `id` | number | |
| `reference` | string | e.g. `SC/2026/000001`. Issued server-side. |
| `storeId` / `storeName` | number / string | |
| `assetTypeName` | string | Null when the whole store was counted. |
| `status` | enum | `DRAFT`, `IN_PROGRESS`, `SUBMITTED`, `APPROVED`, `POSTED`, `CANCELLED` |
| `blind` | boolean | |
| `countedByName` / `approvedByName` | string | |
| `submittedAt` / `approvedAt` / `postedAt` | date-time | |
| `notes` / `reviewRemarks` | string | `reviewRemarks` carries send-back and drift explanations. |
| `totalLines` / `countedLines` / `linesWithVariance` | number | Drive the progress indicator. |
| `netUnitVariance` | number | Signed total across all lines. |
| `readyToSubmit` | boolean | True once every line is counted. |
| `lines` | array | See below. |

### `CountLineView`

| Field | Type | Notes |
|---|---|---|
| `commodityId` / `commodityName` | number / string | |
| `assetTypeName` / `unit` | string | |
| `systemQuantity` | number \| null | **Null while a blind count is being counted.** |
| `countedQuantity` | number \| null | Null means not counted yet. |
| `variance` | number \| null | Null while withheld. |
| `reason` | enum \| null | |
| `note` | string | |
| `recountRequired` | boolean | Set when the balance drifted during the count. |
| `countedAt` | date-time | |

### Reason Codes

| Code | Use for |
|---|---|
| `MISCOUNT` | The earlier figure was simply wrong; no physical loss. |
| `DAMAGED` | Physically present but unusable. |
| `LOST_OR_STOLEN` | Missing with no explanation — worth investigating. |
| `EXPIRED` | Past its usable date. |
| `FOUND` | More on the shelf than recorded. |
| `DATA_ENTRY_ERROR` | A keying or posting error elsewhere. |
| `OTHER` | Anything else. **A note is mandatory.** |

---

## Permissions

| Permission | Grants |
|---|---|
| `READ_STOCK_TAKE` | View counts and the variance report. |
| `PERFORM_STOCK_TAKE` | Open a count, record quantities, submit. |
| `APPROVE_STOCK_TAKE` | Approve, send back, post. |

Counting and approving are separate duties on purpose. The system additionally refuses to let the
same person approve a count they performed, regardless of the permissions they hold.

---

## Error Handling

All errors are `ProblemDetail` responses. The `detail` field is written to be shown to the user
directly — it names the commodities involved rather than describing a rule in the abstract.

| Status | Meaning |
|---|---|
| `400` | A workflow rule was broken. See `detail`. |
| `404` | The count, store, category or commodity does not exist. |
| `409` | Someone else changed a balance concurrently. Refresh and retry. |

---

## Not Yet Covered — Second Release

**Serialized assets.** Assets live on `Asset.currentStore`, not as balance rows, so they are absent
from this workflow entirely. They still need tracking: an asset sits in a store just as consumables
do, and it can equally go missing.

The asset equivalent is a **verification sweep** rather than a count:

- list every asset the system says the store holds;
- tick each one seen, by engraved or serial number;
- unticked assets become exceptions — missing, under repair elsewhere, or issued without a record;
- assets found in the store that the system placed elsewhere are relocated to it.

The outcome is an exception list requiring explanation, not a quantity variance — an asset is present
or it is not. The `stock_adjustment` reason codes carry over (`LOST_OR_STOLEN`, `DAMAGED`,
`DATA_ENTRY_ERROR`), and the same separation of counting from approving applies.
