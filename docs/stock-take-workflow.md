# Stock Take Workflow Documentation

## Overview

A stock take is a physical count of what a store actually holds, followed by a correction of the
system balances to match. This document covers the model, the workflow and **exactly what is updated
by which action**. For request/response shapes see `stock-take-api.md`.

Scope: **consumables**. Serialized assets are a second release — see the end of this document.

Two rules govern everything:

> **1. Nothing touches a balance until POST.** Counting, submitting and approving change no stock
> whatsoever. An abandoned, cancelled or rejected count leaves the store exactly as it found it.
>
> **2. Every posted variance leaves a reason-coded `stock_adjustment` row.** A balance is never
> quietly rewritten.

Rule 2 is the reason the feature is shaped this way. Receipts and issues already have documents
behind them — a GRN, an issuance record — so you can always ask why they happened. An adjustment had
nothing: the balance simply became a different number, which is indistinguishable from a bug.

---

## The Model

| Entity | Table | What it is |
|---|---|---|
| `StockCount` | `stock_count` | One physical count of a store: status, who counted, who approved, timestamps. |
| `StockCountLine` | `stock_count_line` | One commodity on the sheet: snapshot, counted quantity, reason, note. |
| `StockAdjustment` | `stock_adjustment` | **Immutable** record of a balance being corrected, and why. |

`stock_adjustment` is append-only by design. A wrong adjustment is corrected by counting again and
posting another, so the history stays a truthful account of what was believed and when. It is also
the first slice of the wider stock ledger — deliberately the slice where the reason matters most.

---

## The Workflow

```
  OPEN ──────► COUNT ──────► SUBMIT ──────► APPROVE ──────► POST
  DRAFT      IN_PROGRESS    SUBMITTED      APPROVED        POSTED
                                 │
                                 └── send back ──► IN_PROGRESS

  cancel (any time before POSTED) ──► CANCELLED
```

### What each step updates

| Step | Balances | Count sheet | Adjustments |
|---|---|---|---|
| **Open** | — | Sheet created; every commodity the store holds snapshotted as `systemQuantityAtStart` | — |
| **Count** | — | `countedQuantity`, `reason`, `note` saved per line; status → `IN_PROGRESS` | — |
| **Submit** | — | Status → `SUBMITTED`; **system quantities and variances revealed** | — |
| **Approve** | — | Status → `APPROVED`; approver and timestamp recorded | — |
| **Send back** | — | Status → `IN_PROGRESS` with mandatory remarks | — |
| **Post** | **credited or debited per line, via `InventoryService`** | Status → `POSTED` | **one row per changed line** |
| **Cancel** | — | Status → `CANCELLED`, with a required reason. An approver may cancel anything short of `POSTED`; the counter may cancel their own count while it is still being counted | — |

---

## Step by Step

### 1 — Open

Opening **snapshots** what the system currently believes for every commodity the store holds.
Variances are measured against that snapshot, not against a live figure that may move while the
shelves are being counted.

Only one unfinished count per store is allowed — two people counting the same shelves at once produce
two conflicting truths.

Optionally narrow the sheet to a single asset category rather than the whole store.

### 2 — Count (blind by default)

**The counter does not see the system quantity.** `systemQuantity` and `variance` come back as `null`
on every line until the sheet is submitted — they are genuinely withheld by the server, not merely
hidden in the UI.

This is deliberate: showing the expected figure invites confirming it rather than counting the shelf,
and it is the single biggest determinant of whether a stock take finds problems or rubber-stamps
them.

Counting can be partial. Save as often as you like; only the lines you send are touched.

### 3 — Submit

Three checks, all of which must pass:

| Check | Why |
|---|---|
| Every line counted | **A blank is not a zero.** Treating one as zero would write off stock nobody looked for. |
| Every non-zero variance has a reason | A variance without a reason is just an unexplained number. |
| Reason `OTHER` carries a note | Otherwise it explains nothing. |

Error messages name the offending commodities, e.g.
*"5 line(s) have not been counted yet — a blank is not a zero. Outstanding: Fan, Generator, Keyboard…"*

On submission the system quantities and variances are revealed, and this same sheet becomes the
variance review.

### 4 — Approve, or send back

**The person who counted cannot approve their own count** — enforced regardless of permissions held.
Approving records who accepted the variances and still changes no stock.

Sending back returns the sheet to `IN_PROGRESS` with mandatory remarks so the counter knows what to
redo.

### 5 — Post

Before anything is written, each line's *current* balance is compared with the snapshot taken when the
sheet was opened.

**If stock moved in between, nothing is posted.** The count is no longer a statement about the
present, and neither available answer is correct: `counted − snapshot` ignores the movement, while
`counted − current` double-counts it. Rather than pick a wrong number:

- the affected lines are flagged `recountRequired`,
- the sheet returns to `IN_PROGRESS` with an explanation,
- the request fails with `400`,
- **and nothing is adjusted.**

Recounting a flagged line clears the flag.

> Implementation note: the drift check commits its findings in a separate transaction and *then*
> refuses. Run inside the posting transaction, throwing would roll back the very recount flags that
> tell the counter which lines to redo — a refusal that is correct but useless.

When there is no drift, each changed line is applied through `InventoryService` — the same path every
other stock movement uses — and one `stock_adjustment` row is written per changed line, capturing
before, after, delta, reason, note, approver and poster.

---

## Reason Codes

Required on every non-zero variance.

| Code | Use for |
|---|---|
| `MISCOUNT` | The earlier figure was wrong; nothing physically lost |
| `DAMAGED` | Physically present but unusable |
| `LOST_OR_STOLEN` | Missing with no explanation — worth investigating |
| `EXPIRED` | Past its usable date |
| `FOUND` | More on the shelf than recorded |
| `DATA_ENTRY_ERROR` | A keying or posting mistake elsewhere |
| `OTHER` | Anything else — **a note is mandatory** |

The reason is what makes the variance report worth reading, and what separates a counting mistake
from a loss that needs investigating.

---

## Worked Example

Head Office Admin store holds Chair 7, Keyboard 2, and four other lines.

| Step | What happened | Result |
|---|---|---|
| Open | Sheet `SC/2026/000001`, 6 lines | System quantities withheld |
| Count | Chair 5, Keyboard 3, rest as expected | — |
| Submit (first try) | Reasons missing | `400` — *"A reason is required for every variance. Missing on: Chair, Keyboard"* |
| Count again | Chair → `DAMAGED` "Two chairs broken", Keyboard → `FOUND` "Unrecorded return" | — |
| Submit | Passes | Revealed: Chair −2, Keyboard +1, net −1 |
| Approve (as the counter) | Refused | `400` — *"A count cannot be approved by the person who counted it."* |
| Approve (as someone else) | Accepted | Status `APPROVED`; **no stock changed yet** |
| Post | Applied | Chair **7 → 5**, Keyboard **2 → 3**; 2 adjustments written |

Variance report afterwards: 2 lines, 1 unit up, 2 units down, grouped `DAMAGED −2` and `FOUND +1`,
each linked back to `SC/2026/000001`.

---

## Variance Report

`GET /api/v1/stock-adjustments?storeId=&from=&to=` returns every adjustment with totals by reason and
the units written up and down. This is the report that makes patterns visible — a store that keeps
producing `LOST_OR_STOLEN` variances is telling you something a single count never could.

---

## Permissions

| Permission | Grants |
|---|---|
| `READ_STOCK_TAKE` | View counts and the variance report |
| `PERFORM_STOCK_TAKE` | Open a count, record quantities, submit; cancel their own unsubmitted count |
| `APPROVE_STOCK_TAKE` | Approve, send back, post, cancel |

Counting and approving are separate duties on purpose. **These permissions are seeded to the
super-admin role only — grant them to your storekeeper and approver roles before use.**

---

## Second Release — Serialized Assets

Assets live on `Asset.currentStore`, not as balance rows, so they are absent from this workflow
entirely. They still need tracking: an asset sits in a store just as consumables do, and can equally
go missing.

The asset equivalent is a **verification sweep** rather than a count:

- list every asset the system says the store holds;
- tick each one seen, by engraved or serial number;
- unticked assets become exceptions — missing, under repair elsewhere, or issued without a record;
- assets found in the store that the system placed elsewhere are relocated to it.

The outcome is an exception list requiring explanation, not a quantity variance — an asset is present
or it is not. The reason codes carry over (`LOST_OR_STOLEN`, `DAMAGED`, `DATA_ENTRY_ERROR`), as does
the separation of counting from approving.

## Related

- `stock-take-api.md` — endpoint reference for this workflow
- `inventory-stocking-workflow.md` — how stock enters the system
- `movement-workflow.md` — how stock moves between stores
