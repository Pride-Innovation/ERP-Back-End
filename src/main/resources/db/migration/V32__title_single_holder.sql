-- A title may be marked "single holder": only one active account per location (a branch, Head
-- Office counting as one) may hold it. Branch Manager, BOM and heads of department are the cases it
-- exists for: approval routing picks the holder of a title at the requester's branch, and with two
-- holders it had no rule for which one.
--
-- Every title starts as allowing several holders, so nothing is refused on the day this ships; the
-- setting is switched on per title from Settings -> Titles. Switching it on is refused while any
-- location already has two active holders, so the rule is never on while broken.
--
-- Title is not @Audited, so there is no title_aud to extend (trap #5).

ALTER TABLE title ADD COLUMN single_holder BIT(1) NOT NULL DEFAULT b'0';
