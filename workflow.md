# Test Workflows — Head Office & Branches

Concrete approval workflows to create for **manual testing** of the request →
acknowledgement → issuance → movement → store-update pipeline.

Because a workflow's `assetTypes` field holds **multiple** categories, you only
need **4 workflows** (Admin vs Infrastructure × Head Office vs Branch), each
listing the categories it owns.

---

## How the engine picks a workflow

`ApprovalWorkflowRepository.findActiveWorkflowsForRequest` matches on four things
and selects the **most specific, lowest-priority** candidate:

- **Scope** — derived from the requester's branch: `isHeadOffice` → `HEAD_OFFICE`,
  else `BRANCH`. Matches when the workflow's `branchScope` equals that or is `ALL`.
- **Branch** — workflow's `branchId` equals the requester's branch, or is null
  (= all branches in scope).
- **Asset category** — workflow's `assetTypes` is empty (any) or contains the
  request's category.
- **Requester roles** — empty (any) or intersects the requester's effective roles.

Two hard ceilings enforced by `ApproverChainResolver`:

| Scope        | Available approver tiers                          | Ceiling          |
|--------------|---------------------------------------------------|------------------|
| Head Office  | `SUPERVISOR` → `MANAGER` → `DEPT_HEAD`             | `DEPT_HEAD`      |
| Branch       | `SUPERVISOR` → `BOM` → `BRANCH_MANAGER`            | `BRANCH_MANAGER` |

> A request never climbs past the ceiling. Don't put `MANAGER`/`DEPT_HEAD` on a
> branch workflow or `BOM`/`BRANCH_MANAGER` on a Head Office workflow — those
> tiers won't resolve and the step is silently skipped.

---

## Category → handling unit

| Handling unit        | Categories                                                                                                       |
|----------------------|------------------------------------------------------------------------------------------------------------------|
| **Infrastructure Unit** | Computers, Equipment, Financial & Banking Equipment                                                            |
| **Admin Unit**          | Furniture, Stationery, Vehicle/Fleet, Security, Cleaning & Hygiene, Healthcare, Printing & Branding, Building & Construction, Kitchen & Groceries |

The split is arbitrary for testing — what matters is that each category lands in
exactly one workflow per scope so routing is unambiguous.

---

## Shared post-approval tail

All four workflows share the same trailing steps; only the **Unit** differs:

| # | stepType            | approverType            | notify                                            |
|---|---------------------|-------------------------|---------------------------------------------------|
| 4 | ACKNOWLEDGE_REQUEST | GROUP_EMAIL → *Unit*     | requester gets "now at Admin/Infrastructure"      |
| 5 | ISSUANCE            | GROUP_EMAIL → *Unit*     | `notifyOnCompletion = {REQUESTER}` → "ready for picking" |
| 6 | ACKNOWLEDGE_RECEIPT | REQUESTER               | `notifyOnCompletion = {ADMIN, REQUESTER_DIRECT_SUPERVISOR}` |

---

## The 4 workflows

### 1. HO · Admin-handled

```
name:         HO · Admin-handled
branchScope:  HEAD_OFFICE
branchId:     (null)
assetTypes:   Furniture, Stationery, Vehicle/Fleet, Security,
              Cleaning & Hygiene, Healthcare, Printing & Branding,
              Building & Construction, Kitchen & Groceries
priority:     10
steps:        1 REQUEST_APPROVAL    SUPERVISOR
              2 REQUEST_APPROVAL    MANAGER
              3 REQUEST_APPROVAL    DEPT_HEAD
              4 ACKNOWLEDGE_REQUEST  GROUP_EMAIL → Admin Unit
              5 ISSUANCE             GROUP_EMAIL → Admin Unit
              6 ACKNOWLEDGE_RECEIPT  REQUESTER
```

### 2. HO · Infrastructure-handled

```
name:         HO · Infrastructure-handled
branchScope:  HEAD_OFFICE
branchId:     (null)
assetTypes:   Computers, Equipment, Financial & Banking Equipment
priority:     11
steps:        1 REQUEST_APPROVAL    SUPERVISOR
              2 REQUEST_APPROVAL    MANAGER
              3 REQUEST_APPROVAL    DEPT_HEAD
              4 ACKNOWLEDGE_REQUEST  GROUP_EMAIL → Infrastructure Unit
              5 ISSUANCE             GROUP_EMAIL → Infrastructure Unit
              6 ACKNOWLEDGE_RECEIPT  REQUESTER
```

### 3. Branch · Admin-handled

```
name:         Branch · Admin-handled
branchScope:  BRANCH
branchId:     (null = all branches)
assetTypes:   Furniture, Stationery, Vehicle/Fleet, Security,
              Cleaning & Hygiene, Healthcare, Printing & Branding,
              Building & Construction, Kitchen & Groceries
priority:     20
steps:        1 REQUEST_APPROVAL    SUPERVISOR        (Head Cashier; skips if absent)
              2 REQUEST_APPROVAL    BOM               (Branch Operations Manager)
              3 REQUEST_APPROVAL    BRANCH_MANAGER    (ceiling)
              4 ACKNOWLEDGE_REQUEST  GROUP_EMAIL → Admin Unit
              5 ISSUANCE             GROUP_EMAIL → Admin Unit
              6 ACKNOWLEDGE_RECEIPT  REQUESTER
```

### 4. Branch · Infrastructure-handled

```
name:         Branch · Infrastructure-handled
branchScope:  BRANCH
branchId:     (null)
assetTypes:   Computers, Equipment, Financial & Banking Equipment
priority:     21
steps:        1 REQUEST_APPROVAL    SUPERVISOR
              2 REQUEST_APPROVAL    BOM
              3 REQUEST_APPROVAL    BRANCH_MANAGER
              4 ACKNOWLEDGE_REQUEST  GROUP_EMAIL → Infrastructure Unit
              5 ISSUANCE             GROUP_EMAIL → Infrastructure Unit
              6 ACKNOWLEDGE_RECEIPT  REQUESTER
```

---

## Prerequisites (the engine reads these live)

Without these, steps silently skip or emails go nowhere:

1. **Head Office branch** (`shortCode 0`) flagged `isHeadOffice = true`.
2. **A test branch** (e.g. Mbarara `201`) with `branchManager` and
   `branchOperationsManager` users assigned, plus `managersGroupEmail` set.
3. **Each department** used: `headOfDepartment` assigned + `managersGroupEmail` set
   (the HO ladder and rejection fan-out read this).
4. **Two Units created** (they are NOT seeded): `Admin Unit` and
   `Infrastructure Unit`, each with a `groupEmail` and **members assigned**
   (in-app unit notifications go to every member).
5. **Asset categories split** per the table above.
6. **`application.admin.email`** points to a real test user (for `ADMIN` approver
   type + issuance).
7. The branch's **ADMIN store auto-creates on first stock-in** — no manual setup.
   To test inter-location movement, stock into the **HO ADMIN store** and fulfil
   the branch request from there.

---

## Minimal first test pass

Create all 4, then raise:

| Workflow      | Request as              | Category    | Exercises                                              |
|---------------|-------------------------|-------------|--------------------------------------------------------|
| HO · Admin    | Administration Officer  | Furniture   | Supervisor → Manager → HoD, Admin Unit ack             |
| HO · Infra    | Administration Officer  | Computers   | Routes to Infrastructure (not Admin)                   |
| Branch · Admin| Teller                  | Stationery  | Head Cashier → BOM → Branch Manager                    |
| Branch · Infra| Teller (fulfil from HO store) | Computers | Full movement ladder + store ledger                |

### What to verify

- **Request + approval:** approver gets email **and** in-app bell; managers-group
  is CC'd; request `status` advances at each step
  (`supervisorApproved` → `managerApproved`/`bomApproved` →
  `hodApproved`/`branchManagerApproved`).
- **Rejection:** reject at step 2 or 3 with a comment → requester, every prior
  approver, and the managers-group all notified. Resubmit → old instance becomes
  `SUPERSEDED`, fresh one starts at step 1.
- **Unit acknowledgement:** correct Unit group email + every unit member gets a
  bell; requester told "now at Admin/Infrastructure."
- **Stocking → assets → store:** stock the commodities (incl. a partial-delivery
  case), upload the signed GRN, confirm N assets auto-create under the right
  category, and the branch ADMIN store balance increments.
- **Issuance + movement:**
  - HO request issued from HO ADMIN store → `DIRECT_ISSUANCE` (no movement).
  - Branch request issued from HO ADMIN store → `STORE_MOVEMENT`: walk the
    movement `dispatch → in-transit → receive → complete`; verify
    `RECEIVED_WITH_DISCREPANCY` handling and that the ledger applies on
    completion (HO store ↓, branch store ↑).
- **Final receipt:** requester acknowledges → status `receiptAcknowledged`,
  instance `COMPLETED`, `notifyOnCompletion` targets (admin + supervisor) mailed.

---

## Notes / limitations

- **Admin vs Infrastructure routing requires the asset-category split** — the
  `ACKNOWLEDGE_REQUEST`/`ISSUANCE` steps point at a fixed `Unit`, so there is no
  single workflow that routes by category dynamically. That's why it's 2+2, not
  1+1. Driving routing off `AssetType.ownerGroupEmail` instead would be a code
  change, not config.
- You can fully test **movement** only via a **branch** request fulfilled from a
  different location (e.g. HO store). A Head Office request fulfilled locally is
  `DIRECT_ISSUANCE` and skips the dispatch/receive ladder.
