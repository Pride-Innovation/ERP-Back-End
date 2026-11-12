-- A title says where it is used (Head Office or the branches) and, optionally, which department it
-- belongs to. Both exist only so the Titles page in Settings can filter by them; nothing reads them
-- for permissions, scope or approval routing.
--
-- Both are nullable and start empty: classifying titles is done by hand from the UI, and an empty
-- workplace is offered as "Not set" by the filter so the unclassified ones can be found.
--
-- `workplace` rather than reusing `title.branch_id`: that column names one specific branch, while
-- branches share their titles, and the front end reads it as a fallback for a user's own branch.
--
-- ON DELETE SET NULL: deleting a department clears it from titles rather than being blocked by a
-- classification that exists only for filtering.
--
-- Title is not @Audited, so there is no title_aud to extend (trap #5).

ALTER TABLE title ADD COLUMN workplace VARCHAR(20) NULL;
ALTER TABLE title ADD COLUMN department_id BIGINT NULL;
ALTER TABLE title ADD CONSTRAINT fk_title_department
    FOREIGN KEY (department_id) REFERENCES department (id) ON DELETE SET NULL;
