# ERP Backend Documentation

## Stock, Stores and Movement

Read these three together — they describe one system from three angles: how stock gets **in**, how it
**moves**, and how it is **reconciled** to reality.

| Document | Covers |
|---|---|
| [`inventory-stocking-workflow.md`](inventory-stocking-workflow.md) | Purchase orders, receiving deliveries in batches, GRNs, asset registration, reconciliation |
| [`movement-workflow.md`](movement-workflow.md) | All eight movement types, the dispatch → in-transit → receive lifecycle, the repair round-trip, approvals |
| [`stock-take-workflow.md`](stock-take-workflow.md) | Counting a store and correcting balances, with reason codes |
| [`stock-take-api.md`](stock-take-api.md) | Endpoint reference for the stock take |

### The one-paragraph version

Stock enters through a **receipt** against a purchase order: one physical delivery, one GRN, one
store credit, one batch of assets — all in a single transaction. It then **moves** between stores via
movements, whose effects apply on completion rather than initiation, so goods in a courier's van
belong to neither end. Periodically a **stock take** counts what is actually on the shelf and posts
reason-coded adjustments for any difference.

### The three invariants worth knowing

1. **Quantities only enter through receiving.** The correction form cannot change what has been
   received, because crediting the store, registering assets and issuing a GRN all have to happen
   with it.
2. **Movement effects apply on completion, never at initiation.** In-transit stock is held by the
   courier's custody store, so it is never counted at both ends.
3. **Balances are only ever changed through `InventoryService`**, and a stock-take adjustment always
   carries a reason.

---

## Reference APIs

| Document | Covers |
|---|---|
| [`branch-listing-api.md`](branch-listing-api.md) | Branches |
| [`commodity-api.md`](commodity-api.md) | Commodities |
| [`department-api.md`](department-api.md) | Departments |
| [`region-api.md`](region-api.md) | Regions |
| [`roles-and-permissions-api.md`](roles-and-permissions-api.md) | Roles and permissions |
| [`supplier-api.md`](supplier-api.md) | Suppliers |
| [`title-api.md`](title-api.md) | Titles |
