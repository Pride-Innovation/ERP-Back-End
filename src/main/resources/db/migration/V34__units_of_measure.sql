-- Units of measure: one managed list (Settings -> Units of Measure), and the existing values cleaned
-- to match it.
--
-- A commodity's unit (commodity.group_name) was free text and an asset's (asset.unit_of_measure) was
-- picked from four values written into the page, and nothing linked the two. Measured on UAT before
-- this: commodities used pieces, boxes, litres, lots, ream and gallons; assets held "piece" (28, from
-- the import), "pieces" (2) and one NULL. The units stay stored as text on both tables; this list
-- decides which texts may be chosen from now on.
--
-- Plain UPDATEs on asset do not reach asset_aud - Envers records changes made through the
-- application, and this is a one-off correction of spelling, not a change anyone made.

CREATE TABLE unit_of_measure (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(50) NOT NULL,
  active BIT(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (id),
  UNIQUE KEY uk_unit_of_measure_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Commodities: one spelling per unit, plural like the rest.
UPDATE commodity SET group_name = LOWER(TRIM(group_name)) WHERE group_name IS NOT NULL;
UPDATE commodity SET group_name = NULL WHERE group_name = '';
UPDATE commodity SET group_name = 'reams' WHERE group_name = 'ream';
UPDATE commodity SET group_name = 'pieces' WHERE group_name IN ('piece', 'pcs', 'pc');

-- Assets: the same spellings, and a missing unit taken from the asset's commodity.
UPDATE asset SET unit_of_measure = LOWER(TRIM(unit_of_measure)) WHERE unit_of_measure IS NOT NULL;
UPDATE asset SET unit_of_measure = NULL WHERE unit_of_measure = '';
UPDATE asset SET unit_of_measure = 'pieces' WHERE unit_of_measure IN ('piece', 'pcs', 'pc');
UPDATE asset SET unit_of_measure = 'reams' WHERE unit_of_measure = 'ream';
UPDATE asset a JOIN commodity c ON c.id = a.commodity_id
   SET a.unit_of_measure = c.group_name
 WHERE a.unit_of_measure IS NULL AND c.group_name IS NOT NULL;

-- The list starts as every unit already in use. (On a new database both tables are still empty
-- here; the startup seeding fills the list once the commodities exist.)
INSERT INTO unit_of_measure (name)
SELECT DISTINCT group_name FROM commodity WHERE group_name IS NOT NULL AND CHAR_LENGTH(group_name) <= 50;
INSERT IGNORE INTO unit_of_measure (name)
SELECT DISTINCT unit_of_measure FROM asset WHERE unit_of_measure IS NOT NULL AND CHAR_LENGTH(unit_of_measure) <= 50;
