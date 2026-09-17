-- Backfills asset_type.repair_destination, which has been NULL on every category since the column
-- was added.
--
-- The repair-transfer flow resolves routing in three tiers (RepairMovementService#createRepairTransfer):
-- the destination explicitly chosen on the form, else the category's repair_destination, else a
-- hardcoded IT. With every category NULL, tier two never fired and the hardcoded fallback decided
-- everything — so a faulty chair was routed to the IT store, contradicting the premise the
-- RepairDestination enum documents for itself ("IT does not repair furniture"). Operators had to
-- notice and override on each transfer, and the create/update form silently wrote IT to whichever
-- category was edited next, which would have entrenched the wrong answer permanently.
--
-- Routing rationale: IT covers powered/electronic equipment the in-house workshop handles. ADMIN
-- covers the facilities and furnishings the administration team maintains. EXTERNAL covers items
-- that are always vendor-serviced under warranty or contract, where an in-house workshop has no
-- standing to open the unit — vehicles, and banking hardware such as ATMs, note counters and safes.
--
-- Only NULL rows are touched, so this cannot overwrite a routing anyone has deliberately configured;
-- that also makes it safe against environments where these categories were already set by hand.
-- Matching is by name because these are seeded reference rows whose ids differ between environments.
-- A category not listed here (or renamed) simply stays NULL and keeps falling back to IT.
--
-- Every routing here remains an operational default, not a rule: the transfer form can override it
-- per movement, and Settings -> Asset Types can change it per category at any time.
--
-- asset_type_aud is deliberately NOT rewritten, matching V11 — the Envers trail records what the
-- application actually stored at each revision, and backdating it would falsify that history.

UPDATE asset_type
SET repair_destination = 'IT'
WHERE repair_destination IS NULL
  AND name IN (
      'Computers',
      'Equipment',
      'Security',
      'Printing & Branding'
  );

UPDATE asset_type
SET repair_destination = 'ADMIN'
WHERE repair_destination IS NULL
  AND name IN (
      'Furniture',
      'Stationery',
      'Cleaning & Hygiene',
      'Kitchen & Groceries',
      'Building & Construction',
      'Healthcare'
  );

UPDATE asset_type
SET repair_destination = 'EXTERNAL'
WHERE repair_destination IS NULL
  AND name IN (
      'Vehicle/Fleet',
      'Financial & Banking Equipment'
  );
