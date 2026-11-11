# Approval workflows

The six workflows a request can travel through, what a **new** database gets automatically, and what
to change by hand in **Settings → Approval Workflows** on a database that already exists.

---

## 1. On an existing database: what to do in Settings

The seeder (section 4) writes only into an **empty** workflow table, so it does nothing to an
environment that already has workflows. Make these changes by hand, in this order.

### Workflow changes

| # | Workflow | Change |
|---|---|---|
| 1 | **Branch · Admin-handled** | Remove **Stationery** from its categories. Set **Applies to = Personal**. |
| 2 | **Branch · Infrastructure-handled** | Set **Applies to = Personal**. |
| 3 | **Branch · Store-handled** | **Add** — section 3.5 |
| 4 | **Branch · Store restock** | **Add** — section 3.6 |

Steps 1 and 2 matter as much as the two new workflows:

- **If Branch · Admin-handled keeps Stationery,** a branch Stationery request matches two workflows,
  and the Admin one wins on priority (20 beats 22). The request then goes to Head Office, and the
  branch store is never used.
- **If the branch Admin and Infrastructure workflows stay on "Both",** they also serve restock
  requests, and Branch · Admin-handled wins those on priority (20 beats 23). A restock would then
  climb the ordinary branch approval ladder instead of the restock route.

The two Head Office workflows stay on **Both**. Head Office cannot raise a restock at all.

### Not workflows, but needed

| # | Where | What | Why |
|---|---|---|---|
| 5 | Settings → Roles → **Branch Operations Manager** | grant **Issue Items** (`ISSUE_ITEMS`) | the BOM issues in Branch · Store-handled, and when a BOM raised the request, the BM issues it instead |
| 6 | Settings → Roles → **Branch Manager** | grant **Issue Items** (`ISSUE_ITEMS`) | |
| 7 | Settings → Roles → **Branch Manager** | grant **Approve Issuance** (`APPROVE_ISSUANCE`) | the BM approves the issuance, and when the BM raised the request, the BOM approves it |
| 8 | Settings → Roles → **Branch Operations Manager** | grant **Approve Issuance** (`APPROVE_ISSUANCE`) | |

Measured on the development database: both roles already hold Approve / Reject Request, Create
Request, Read Request and Restock Store, but **neither holds Issue Items or Approve Issuance**.
Without those two permissions a branch Stationery request reaches its Issue step and nobody at the
branch can act on it.

*Side effect of Issue Items:* it lets the holder read the requests they issue against, including
requests from other branches. Editing and deleting them still need the ordinary permissions.

**Already in place, nothing to do:**

- **Restock Store** was granted to Branch Manager and Branch Operations Manager automatically.
- **The reporting lines are right.** The Branch Operations Manager and Branch Relationship Manager
  titles report to Branch Manager, which is what lets a BOM's restock reach their BM.
- **Stationery may be stocked at every branch** (stocking rules).

**Not configuration, but needed for a branch to issue:** the branch's own Admin store must hold the
items. Until it does, the request waits at its Issue step, and both the requester and the BOM are
told why.

---

## 2. How the engine picks a workflow

`ApprovalWorkflowRepository.findActiveWorkflowsForRequest` keeps the active workflows that match:

- **Scope.** The requester's branch is Head Office (`HEAD_OFFICE`) or not (`BRANCH`). The workflow's
  scope must equal that, or be `ALL`.
- **Branch.** The workflow's branch must equal the requester's branch, or be empty (all branches).
- **Category.** The workflow's categories must be empty (any category), or contain the request's
  category.
- **Requester roles.** Empty (anyone), or intersecting the requester's roles.
- **Applies to.** **Personal** and **Restock** serve only that kind of request; **Both** serves either
  kind. A request is a restock when the *Restock the branch store* box is ticked.

Among the matches, the most specific wins (a named branch plus categories, then a branch, then
categories, then neither), and after that the **lowest priority number**. A restock that no
workflow serves is **refused** when it is raised. It never falls back to another route.

**A restock never takes a workflow that issues at the branch** (one whose Issue step goes to the
BOM or BM), whatever its *Applies to* says. Such a workflow would issue the restock out of the very
store it is meant to fill, which is refused, so the request would wait at its Issue step forever.
This happened: request 140, a Gulu restock, was picked up by Branch · Store-handled while that was
set to *Both*, because 22 outranks 23. The engine and the request form's routes preview both apply
this rule (`BranchIssuance.servable`), so the preview names the workflow the request will get.
Setting *Applies to* correctly is still the right fix: the rule is a safety net, not a substitute.

Approval ceilings (`ApproverChainResolver`): Head Office climbs Supervisor → Manager → Head of
Department; a branch climbs Supervisor → BOM → Branch Manager. A tier the requester's reporting
line does not reach is skipped.

---

## 3. The six workflows

Every step has **48 h escalation** and **is not optional**. "CC" is the step's notify-group source;
"On done" is who is told when the step completes; "Skip if" lists requester roles for which the step
is skipped. No seeded step carries a fixed mailbox (section 4).

### 3.1 HO · Admin-handled

| | |
|---|---|
| Scope / applies to / priority | Head Office · Both · 10 |
| Categories | Furniture, Stationery, Vehicle/Fleet, Security, Cleaning & Hygiene, Healthcare, Printing & Branding, Building & Construction, Kitchen & Groceries |

| # | Step | Type | Approver | Resolve against | Unit | CC | On done | Skip if |
|---|---|---|---|---|---|---|---|---|
| 1 | Supervisor Approval | Request approval | Supervisor | Requester | | Requester | Requester | Manager, Head of Department, Supervisor |
| 2 | Manager Approval | Request approval | Manager | Requester | | Requester's managers group | Requester | Manager, Head of Department |
| 3 | HOD Approval | Request approval | Head of Department | Requester | | Requester's managers group | Requester | Head of Department |
| 4 | Admin Acknowledgement | Acknowledge request | Group email | | Admin Unit | Requester's managers group | Requester | |
| 5 | Admin Issuance | Issuance | Group email | | Admin Unit | Requester's managers group | Requester | |
| 6 | Approve Issuance | Request approval | Manager | **Issuer** | | Requester's managers group | Requester | |
| 7 | Acknowledge Receipt | Acknowledge receipt | Requester | | | Requester | — | |

### 3.2 HO · Infrastructure-handled

| | |
|---|---|
| Scope / applies to / priority | Head Office · Both · 11 |
| Categories | Equipment, Computers, Financial & Banking Equipment |

As 3.1, except: step 1's CC is the requester's managers group, step 4 is **Infra Acknowledgement**
(Infra Unit), and step 7's CC is **Admin**. Issuance (step 5) is still by the **Admin Unit**.

### 3.3 Branch · Admin-handled — *edited*

| | |
|---|---|
| Scope / applies to / priority | Branch · **Personal** · 20 |
| Categories | Furniture, Vehicle/Fleet, Security, Cleaning & Hygiene, Healthcare, Printing & Branding, Building & Construction, Kitchen & Groceries — **no Stationery** |

| # | Step | Type | Approver | Resolve against | Unit | CC | On done | Skip if |
|---|---|---|---|---|---|---|---|---|
| 1 | Supervisor Approval | Request approval | Supervisor | Requester | | Requester | — | Manager, Branch Manager, BOM, Supervisor |
| 2 | Manager Approval | Request approval | Manager | Requester | | Requester's managers group | Requester | Manager, Branch Manager, BOM |
| 3 | BOM Approval | Request approval | BOM | Requester | | Requester's managers group | Requester | Branch Manager, BOM |
| 4 | BM Approval | Request approval | Branch Manager | Requester | | Requester's managers group | Requester | Branch Manager |
| 5 | Admin Acknowledgement | Acknowledge request | Group email | | Admin Unit | Requester's managers group | Requester | |
| 6 | Admin Issuance | Issuance | Group email | | Admin Unit | Requester's managers group | Requester | |
| 7 | Approve Issuance | Request approval | Manager | **Issuer** | | Requester's managers group | Requester | |
| 8 | Requester Receipt | Acknowledge receipt | Requester | | | Admin | Requester's managers group | |

*(Step 1 was named "Supervisor Aproval" on the development database; the seed spells it correctly.)*

### 3.4 Branch · Infrastructure-handled — *edited*

| | |
|---|---|
| Scope / applies to / priority | Branch · **Personal** · 21 |
| Categories | Equipment, Computers, Financial & Banking Equipment |

As 3.3, except:

- **Step 1:** its CC is the requester's managers group, it notifies the requester when done, and it
  is skipped for Branch Manager, BOM and Supervisor (not Manager).
- **Step 5:** Infra Acknowledgement, by the Infra Unit.
- **Step 7:** named *Issuance Approval*; on done it tells the requester and Admin.
- **Step 8:** *Acknowledge Receipt*, with CC to the requester's managers group, and it tells the
  requester when done.

### 3.5 Branch · Store-handled — **new**

Branch Stationery is issued at the branch, from the branch's own Admin store. The request never
goes to Head Office.

| | |
|---|---|
| Name | Branch · Store-handled |
| Scope / applies to / priority | Branch · **Personal** · 22 |
| Categories | **Stationery** |

| # | Step | Type | Approver | Resolve against | Unit | CC | On done | Skip if |
|---|---|---|---|---|---|---|---|---|
| 1 | Supervisor Approval | Request approval | Supervisor | Requester | | Requester | — | Manager, Branch Manager, BOM, Supervisor |
| 2 | Manager Approval | Request approval | Manager | Requester | | Requester's managers group | Requester | Manager, Branch Manager, BOM |
| 3 | Branch Issuance | **Issuance** | **BOM** | Requester | *(none)* | Requester's managers group | Requester | |
| 4 | Approve Issuance | Request approval | **Branch Manager** | **Issuer** | *(none)* | Requester's managers group | Requester | |
| 5 | Acknowledge Receipt | Acknowledge receipt | Requester | | | Admin | Requester's managers group | |

**There is no Acknowledge step, on purpose.**

How the engine treats steps 3 and 4 (built in Phase 3; this is not configuration):

- **Maker-checker.** Nobody issues their own request. When the BOM raised it, the **BM issues** it.
  The issuance is approved by someone who neither raised it nor issued it, so the BOM and BM swap
  roles as needed.
- **If neither is free, the step waits; it is never skipped.** For example, the BOM raised the
  request and the BM issued it, so nobody is left to approve the issuance. The request page says so.
- **The BOM and BM are found by role at the branch.** No branch has its BM or BOM field filled in;
  the field is used whenever it is set.
- **When the branch store is short,** the request waits at step 3. The requester and the BOM are
  told once, and the BOM is told again when stock lands and the request can be issued.

### 3.6 Branch · Store restock — **new**

The BOM asks Head Office to restock the branch store (the *Restock the branch store* box on the
request form). The BM approves, and the Admin Unit fulfils. The delivery lands **in the branch
store**, not with a person.

| | |
|---|---|
| Name | Branch · Store restock |
| Scope / applies to / priority | Branch · **Restock** · 23 |
| Categories | **Cleaning & Hygiene, Kitchen & Groceries, Printing & Branding, Stationery** |

| # | Step | Type | Approver | Resolve against | Unit | CC | On done | Skip if |
|---|---|---|---|---|---|---|---|---|
| 1 | BM Approval | Request approval | **Branch Manager** | Requester | | Requester's managers group | Requester | Branch Manager |
| 2 | Admin Acknowledgement | Acknowledge request | Group email | | **Admin Unit** | Requester's managers group | Requester | |
| 3 | Admin Issuance | Issuance | Group email | | **Admin Unit** | Requester's managers group | Requester | |
| 4 | Approve Issuance | Request approval | Manager | **Issuer** | | Requester's managers group | Requester | |
| 5 | Acknowledge Receipt | Acknowledge receipt | Requester | | | Admin | Requester's managers group | |

- **Step 1 finds the BM by the BOM's reporting line** (the BOM title reports to Branch Manager).
- **It is skipped when the BM raises the restock**, as on the other branch workflows: Branch Manager
  is the top of the branch ladder, so a BM's restock goes straight to the Admin Unit. To require a
  second approval for a BM's restock instead, remove "Branch Manager" from the step's *Skip if*.
- **Only these four categories can be restocked.** A restock of anything else is refused when it is
  raised, because no restock workflow serves it.

---

## 4. A new database gets all six automatically

`WorkflowSeeder` runs **once**, last in `DataInitializer`:

- **New database (no workflows):** it creates the six workflows above exactly as written.
- **Existing database (has workflows):** it records that it ran and writes nothing.
- **Afterwards it never runs again**, so edits made in Settings are never overwritten, and deleting
  every workflow does not bring these back.

**Why a seeder and not a Flyway migration:** a workflow points at categories, units and roles. On a
new database all of those are created by seeders, which run *after* Flyway, so a migration would find
nothing to point at. Writing ids into a migration would hard-code one environment's ids into every
other environment.

**Nothing is identified by id.** Categories are found by name, the Admin and Infra units by the role
they confer (`ADMIN_UNIT`, `INFRA_UNIT`, since units can be renamed), and skip-roles by role name. No
step names a person. If a name cannot be found, that one workflow is skipped and the problem is
logged; the rest are still created.

### Mailboxes

No new variable is needed. A unit step mails the unit's own mailbox, and every other recipient and
CC is looked up when the email is sent: the approver, the requester, their managers' group, Admin.

| Variable | Used for |
|---|---|
| `ADMIN_TEAM_EMAIL_ADDRESS` | the Admin Unit's own mailbox (already required) |
| `IT_TEAM_EMAIL_ADDRESS` | the Infra Unit's own mailbox (already required) |

The seeded steps leave the step's own group and CC address empty. Those two fields are fallbacks:
the CC address is read only for a *Static* CC, which no seeded step uses, and the group address
only when nobody can be found for the step. For a waiting branch issuance step that would mail an
"action required" to somebody who is not allowed to act on it. A fixed address can still be set on
any step in Settings.

*Settings will refuse to save* a group-email step whose unit has no mailbox. If a new environment
starts with `ADMIN_TEAM_EMAIL_ADDRESS` or `IT_TEAM_EMAIL_ADDRESS` blank, set the unit's mailbox on
the Units page before editing those workflows.

`WorkflowSeederTest` checks that every name resolves, the shape of the two new workflows, that each
category has exactly one workflow per scope and request kind, that saving keeps the steps, that a
database with workflows is left alone, and the run-once marker. It deliberately does **not** compare
against the live workflows, because those are edited in Settings. That comparison was done once, when
the seeder was written: all 30 steps of the four existing workflows matched field for field, and the
only differences were the intended ones in section 1.
