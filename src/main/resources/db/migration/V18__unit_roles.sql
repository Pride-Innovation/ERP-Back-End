-- Units confer permissions.
--
-- Until now a unit was a group mailbox with a department — consulted only for GROUP_EMAIL workflow
-- routing and the inbox that follows — so an Admin Unit officer and a branch officer holding the
-- same title role had identical rights over every action in the application. The seniority the bank
-- actually works to was written against role names (ADMIN_OFFICER, ADMIN_MANAGER) that were never
-- seeded, so the code expressing it never executed.
--
-- This table is what gives a unit standing: membership grants the unit's roles, leaving revokes
-- them, and there is one place to configure it.

CREATE TABLE unit_role (
    unit_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (unit_id, role_id),
    CONSTRAINT fk_unit_role_unit FOREIGN KEY (unit_id) REFERENCES unit (id) ON DELETE CASCADE,
    CONSTRAINT fk_unit_role_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ON DELETE CASCADE on both sides: a row here is a grant, not a record. Deleting a unit or a role
-- should take its grants with it rather than leaving rows pointing at nothing.

-- ── Why the permissions and roles themselves are NOT seeded here ────────────
--
-- `permissions` and `roles` both use GenerationType.AUTO, which on MySQL means Hibernate hands out
-- ids from its own sequence table rather than relying on AUTO_INCREMENT. A plain INSERT here fails
-- outright ("Field 'id' doesn't have a default value"), and supplying ids by hand would let the
-- sequence drift until Hibernate later reused one.
--
-- So the DDL lives here and the data seeding lives in UnitRoleSeeder, where Hibernate assigns the
-- ids. That also sidesteps the ordering problem that made Flyway seeding attractive in the first
-- place: the seeder runs from DataInitializer, which is a CommandLineRunner and therefore runs after
-- every migration has been applied.
