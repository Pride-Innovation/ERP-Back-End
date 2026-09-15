-- A staff number identifies one person, and nothing was enforcing it.
--
-- `UserService` checks `existsByStaffNumber` before creating and `existsByStaffNumberAndIdNot`
-- before updating — both correct, and both only a service-level check. Two creations arriving
-- together each read "no such staff number", each pass, and both commit: the database had no opinion.
-- Email had the constraint from the start (on the table and on the column); the staff number did not,
-- which reads as an oversight rather than a decision, because the check beside it shows the intent.
--
-- It also matters more than it looks. The staff number is what the search endpoint matches on and
-- what the directory and the pickers display, so a duplicate does not fail loudly — it produces two
-- rows that look like the same person, and whichever one a picker returns first wins.
--
-- Verified before writing this: 27 accounts, 27 distinct staff numbers, none blank. So this applies
-- cleanly today. If it ever fails on another environment the migration is doing its job — the
-- duplicates have to be resolved by a person, not by a script picking a winner.

ALTER TABLE users
    ADD CONSTRAINT uq_users_staff_number UNIQUE (staff_number);

-- NULL is deliberately still allowed: MySQL treats NULLs as distinct in a unique index, so an
-- account created without a staff number does not collide with another one. That keeps the door open
-- for the bulk import, which can legitimately land a row before the number is known — the service's
-- own check is what refuses a *duplicate* one.
