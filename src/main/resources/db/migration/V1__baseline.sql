-- ─────────────────────────────────────────────────────────────────────────────────────
-- The schema this application starts from.
--
-- Until this file existed the migrations began at V2, and `baseline-on-migrate` told
-- Flyway to *assume* a version-1 schema was already present. That assumption holds on a
-- database Hibernate built years ago and is false on an empty one, so a brand-new
-- environment failed on its first startup: V2's guards test for a *column*
-- (information_schema.COLUMNS), not for the table, so on an empty database the guard
-- reads "column absent", emits `ALTER TABLE stock ADD COLUMN po_number ...`, and MySQL
-- answers that `stock` does not exist. The application could not build its own database.
--
-- This file is that missing version 1: every table as it stood immediately before V2.
--
-- ## It cannot affect a database that already exists
--
-- Flyway ignores any migration at or below the baseline version recorded in
-- `flyway_schema_history`. Every existing environment has a BASELINE row at version 1,
-- so this file is reported as BASELINE_IGNORED and never executed there. Verified
-- against a clone of the development database carrying its real 25-row history: with a
-- V1 that would have created a visibly-named marker table, `validate` passed,
-- `migrate` executed 0 migrations, and the marker was never created.
--
-- That is also why this had to arrive as a *new* file rather than as edits to the
-- existing ones. Changing an applied migration changes its checksum, and every database
-- that already ran it then fails validation on the next startup.
--
-- ## What it deliberately does not contain
--
--   * The twelve tables V5, V7, V10, V12, V17, V18, V19 and V22 create -- they are
--     created by those migrations, which run straight after this one.
--   * `password_reset`. V21 drops it with `IF EXISTS`, so a new database never needs to
--     create a table whose only future is to be dropped.
--   * Business data. Flyway owns the schema; the reference data (statuses, permissions,
--     roles, branches, stores) is seeded in Java by `DataInitializer` on first startup.
--
-- `app_notification.link` and `app_notification.request_id` *are* here, and must stay:
-- V24 drops them unguarded, and MySQL has no `DROP COLUMN IF EXISTS`.
--
-- ## Regenerating it
--
-- Do not hand-edit. It was derived from a `mysqldump --no-data` of a database at V25 by
-- undoing exactly what V2..V25 add, then verified by building an empty database from
-- V1..V25 and diffing every column, index and foreign key against the reference
-- database. `BaselineMigrationTest` pins that round trip.
-- ─────────────────────────────────────────────────────────────────────────────────────

-- Tables are emitted in alphabetical order, so foreign keys routinely name a table that
-- comes later in the file. Disabling the check for the duration lets the whole schema
-- load in one pass; it is restored at the end, and Flyway runs every statement of one
-- migration on a single connection, so the setting covers exactly this file.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `acknowledge_issuance_receipt` (
  `id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlkpm37snp49q8041sje4oxchn` (`request_id`),
  KEY `FKaryrglocsr3af1fuhrdokvjaq` (`user_id`),
  CONSTRAINT `FKaryrglocsr3af1fuhrdokvjaq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlkpm37snp49q8041sje4oxchn` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `acknowledge_issuance_receipt_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FK1e0i0kjxaxvk73a6a9xkex7hy` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `acknowledge_issuance_receipt_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `acknowledge_request_receipt` (
  `id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK26sh6prf68k6ai305pwjol5cl` (`request_id`),
  KEY `FKek5nks0gyw1kms74kw4e5b439` (`user_id`),
  CONSTRAINT `FK26sh6prf68k6ai305pwjol5cl` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FKek5nks0gyw1kms74kw4e5b439` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `acknowledge_request_receipt_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FK6vrcm3ekllvfomy9jnbedok5t` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `acknowledge_request_receipt_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_date` datetime(6) DEFAULT NULL,
  `is_read` bit(1) NOT NULL,
  `message` text,
  `title` varchar(255) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  `link` varchar(255) DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3epo914v596he529dur0nfcpl` (`recipient_id`),
  CONSTRAINT `FK3epo914v596he529dur0nfcpl` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approver_type` varchar(30) NOT NULL,
  `escalation_hours` int NOT NULL,
  `group_email` varchar(255) DEFAULT NULL,
  `notify_group_email` varchar(255) DEFAULT NULL,
  `notify_group_source` varchar(40) NOT NULL DEFAULT 'STATIC',
  `optional` bit(1) NOT NULL,
  `specific_user_id` bigint DEFAULT NULL,
  `step_name` varchar(255) NOT NULL,
  `step_order` int NOT NULL,
  `step_type` varchar(30) NOT NULL,
  `unit_id` bigint DEFAULT NULL,
  `workflow_id` bigint NOT NULL,
  `approver_subject` varchar(20) NOT NULL DEFAULT 'REQUESTER',
  PRIMARY KEY (`id`),
  KEY `FK8nl2j7l9t9owmp335dni6ki2f` (`unit_id`),
  KEY `FKdnh67akehapdnk36av0sk1fex` (`workflow_id`),
  CONSTRAINT `FK8nl2j7l9t9owmp335dni6ki2f` FOREIGN KEY (`unit_id`) REFERENCES `unit` (`id`),
  CONSTRAINT `FKdnh67akehapdnk36av0sk1fex` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_step_notify_on_completion` (
  `step_id` bigint NOT NULL,
  `notify_source` enum('STATIC','REQUESTER_MANAGERS_GROUP','REQUESTER','REQUESTER_DIRECT_SUPERVISOR','ADMIN','NONE') NOT NULL,
  PRIMARY KEY (`step_id`,`notify_source`),
  CONSTRAINT `FKguk1od68fwy943kstnn7s6hy9` FOREIGN KEY (`step_id`) REFERENCES `approval_step` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `branch_scope` enum('HEAD_OFFICE','BRANCH','ALL') NOT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `description` text,
  `last_modified` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `priority` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_workflow_asset_type` (
  `workflow_id` bigint NOT NULL,
  `asset_type_id` bigint NOT NULL,
  PRIMARY KEY (`workflow_id`,`asset_type_id`),
  KEY `FK9o7sbr0ydygr4yp3y2bo9aoyg` (`asset_type_id`),
  CONSTRAINT `FK9o7sbr0ydygr4yp3y2bo9aoyg` FOREIGN KEY (`asset_type_id`) REFERENCES `asset_type` (`id`),
  CONSTRAINT `FKqnkk6nrkyv5afu7b1rxxg0y05` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_workflow_requester_role` (
  `workflow_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`workflow_id`,`role_id`),
  KEY `FKgxjy4kpjm7gkr6yd2pymdnlhh` (`role_id`),
  CONSTRAINT `FKg5urvmffl5tjg6hcw84g117qa` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`),
  CONSTRAINT `FKgxjy4kpjm7gkr6yd2pymdnlhh` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `approval_workflow_step_skip_role` (
  `step_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`step_id`,`role_id`),
  KEY `FKc189g53nu41tu2gca4mde71c5` (`role_id`),
  CONSTRAINT `FKc189g53nu41tu2gca4mde71c5` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKf2164x6lhxjw7e22n9ewctmya` FOREIGN KEY (`step_id`) REFERENCES `approval_step` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset` (
  `id` bigint NOT NULL,
  `asset_depreciation_rate` varchar(255) DEFAULT NULL,
  `asset_name` varchar(255) DEFAULT NULL,
  `cost_of_the_asset` decimal(19,4) DEFAULT NULL,
  `cpu_speed` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `date_receipt` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `detail_net_book_value` varchar(255) DEFAULT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `engraved_number` varchar(255) DEFAULT NULL,
  `hard_disk_size` varchar(255) DEFAULT NULL,
  `hostname` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `interface_type` varchar(255) DEFAULT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `last_repaired_by` varchar(255) DEFAULT NULL,
  `mac_address` varchar(255) DEFAULT NULL,
  `make` varchar(255) DEFAULT NULL,
  `model` varchar(255) DEFAULT NULL,
  `net_valueb` varchar(255) DEFAULT NULL,
  `purchase_cost` decimal(19,4) DEFAULT NULL,
  `ram` varchar(255) DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `tag_name` varchar(255) DEFAULT NULL,
  `unit_of_measure` varchar(255) DEFAULT NULL,
  `status_id` bigint DEFAULT NULL,
  `asset_type_id` bigint DEFAULT NULL,
  `assigned_to_id` bigint DEFAULT NULL,
  `branch_id` bigint DEFAULT NULL,
  `commodity_id` bigint DEFAULT NULL,
  `current_store_id` bigint DEFAULT NULL,
  `stock_id` bigint DEFAULT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `temporary_pool` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_40ixkldrr3u33nslfjxve2bs` (`tag_name`),
  KEY `FKh0hr0ht1icl555gkicoa2fhr6` (`status_id`),
  KEY `FK4udnqglbxg2lpibsun3s0mtf6` (`asset_type_id`),
  KEY `FK7vk5bop4qcw6ulfgypk73q40t` (`assigned_to_id`),
  KEY `FKo3fkuqc80u8iuly95pwe03g6d` (`branch_id`),
  KEY `FK9lhv0kfm4xa22ydyfwt595orb` (`commodity_id`),
  KEY `FKasp6opwyt1152whbtvkb5jgfc` (`current_store_id`),
  KEY `FK66gs1ayqxswk2mcfwgh1qlqjf` (`stock_id`),
  KEY `FKtkl1m8s9s42gsl23jkbrsvg26` (`supplier_id`),
  CONSTRAINT `FK4udnqglbxg2lpibsun3s0mtf6` FOREIGN KEY (`asset_type_id`) REFERENCES `asset_type` (`id`),
  CONSTRAINT `FK66gs1ayqxswk2mcfwgh1qlqjf` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`),
  CONSTRAINT `FK7vk5bop4qcw6ulfgypk73q40t` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9lhv0kfm4xa22ydyfwt595orb` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKasp6opwyt1152whbtvkb5jgfc` FOREIGN KEY (`current_store_id`) REFERENCES `store_container` (`id`),
  CONSTRAINT `FKh0hr0ht1icl555gkicoa2fhr6` FOREIGN KEY (`status_id`) REFERENCES `status` (`id`),
  CONSTRAINT `FKo3fkuqc80u8iuly95pwe03g6d` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
  CONSTRAINT `FKtkl1m8s9s42gsl23jkbrsvg26` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_assignment_history` (
  `id` bigint NOT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `temporary` bit(1) NOT NULL,
  `asset_id` bigint NOT NULL,
  `status_after_id` bigint DEFAULT NULL,
  `status_before_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoo3io5m4bo0333ccdjuo99h1q` (`asset_id`),
  KEY `FKb4aku5893bgnxsyu2835py9j1` (`status_after_id`),
  KEY `FKtfdx2nw5yl2bvhe9o4rhgwxds` (`status_before_id`),
  KEY `FKemam8qgac4eegc3nqeggf245i` (`user_id`),
  CONSTRAINT `FKb4aku5893bgnxsyu2835py9j1` FOREIGN KEY (`status_after_id`) REFERENCES `status` (`id`),
  CONSTRAINT `FKemam8qgac4eegc3nqeggf245i` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKoo3io5m4bo0333ccdjuo99h1q` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`),
  CONSTRAINT `FKtfdx2nw5yl2bvhe9o4rhgwxds` FOREIGN KEY (`status_before_id`) REFERENCES `status` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_assignment_history_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `asset_depreciation_rate` varchar(255) DEFAULT NULL,
  `asset_name` varchar(255) DEFAULT NULL,
  `cost_of_the_asset` decimal(19,4) DEFAULT NULL,
  `cpu_speed` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `date_receipt` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `detail_net_book_value` varchar(255) DEFAULT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `engraved_number` varchar(255) DEFAULT NULL,
  `hard_disk_size` varchar(255) DEFAULT NULL,
  `hostname` varchar(255) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `interface_type` varchar(255) DEFAULT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `last_repaired_by` varchar(255) DEFAULT NULL,
  `mac_address` varchar(255) DEFAULT NULL,
  `make` varchar(255) DEFAULT NULL,
  `model` varchar(255) DEFAULT NULL,
  `net_valueb` varchar(255) DEFAULT NULL,
  `purchase_cost` decimal(19,4) DEFAULT NULL,
  `ram` varchar(255) DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `tag_name` varchar(255) DEFAULT NULL,
  `unit_of_measure` varchar(255) DEFAULT NULL,
  `temporary_pool` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKk2y69n6xuwan2iwwis54utbke` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_type` (
  `id` bigint NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `field_config` longtext,
  `name` varchar(255) DEFAULT NULL,
  `owner_group_email` varchar(255) DEFAULT NULL,
  `short_code` varchar(255) DEFAULT NULL,
  `tracks_assets` bit(1) NOT NULL DEFAULT b'0',
  `depreciation_rate` double DEFAULT NULL,
  `residual_percent` double DEFAULT NULL,
  `useful_life_months` int DEFAULT NULL,
  `repair_destination` enum('IT','ADMIN','EXTERNAL') DEFAULT NULL,
  `repairable` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_type_custom_attribute` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_type` enum('TEXT','NUMBER','DATE','BOOLEAN','SELECT') NOT NULL,
  `display_order` int NOT NULL,
  `helper_text` varchar(255) DEFAULT NULL,
  `attribute_key` varchar(80) NOT NULL,
  `label` varchar(120) NOT NULL,
  `options_json` longtext,
  `is_required` bit(1) NOT NULL,
  `asset_type_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FKgjurwga0d3iyh3lyki3cad01g` FOREIGN KEY (`asset_type_id`) REFERENCES `asset_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `asset_type_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `branch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) DEFAULT NULL,
  `is_head_office` bit(1) NOT NULL DEFAULT b'0',
  `managers_group_email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `short_code` varchar(255) DEFAULT NULL,
  `telephone` varchar(255) DEFAULT NULL,
  `branch_manager_id` bigint DEFAULT NULL,
  `branch_operations_manager_id` bigint DEFAULT NULL,
  `credit_administrator_id` bigint DEFAULT NULL,
  `district_id` bigint DEFAULT NULL,
  `region_id` bigint DEFAULT NULL,
  `relationship_manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsbcy2f2tqc3am65ut4c8fgcde` (`branch_manager_id`),
  KEY `FKs3bonmqay55wera5qwwfoxr4y` (`branch_operations_manager_id`),
  KEY `FK322pt1tkx83oev5fqxbiwmw6y` (`credit_administrator_id`),
  KEY `FK2rrq2mpwdv4kr8wj2o4m54wgw` (`district_id`),
  KEY `FKmj6hqpkrjq6qw6q71rknd9jmx` (`region_id`),
  KEY `FKawq13rjbilwkavq7q5gwwkcha` (`relationship_manager_id`),
  CONSTRAINT `FK2rrq2mpwdv4kr8wj2o4m54wgw` FOREIGN KEY (`district_id`) REFERENCES `district` (`id`),
  CONSTRAINT `FK322pt1tkx83oev5fqxbiwmw6y` FOREIGN KEY (`credit_administrator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKawq13rjbilwkavq7q5gwwkcha` FOREIGN KEY (`relationship_manager_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmj6hqpkrjq6qw6q71rknd9jmx` FOREIGN KEY (`region_id`) REFERENCES `region` (`id`),
  CONSTRAINT `FKs3bonmqay55wera5qwwfoxr4y` FOREIGN KEY (`branch_operations_manager_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsbcy2f2tqc3am65ut4c8fgcde` FOREIGN KEY (`branch_manager_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `commodity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_name` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `asset_type_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5xvrrq8jmf3o589g0y3ry7ugm` (`asset_type_id`),
  CONSTRAINT `FK5xvrrq8jmf3o589g0y3ry7ugm` FOREIGN KEY (`asset_type_id`) REFERENCES `asset_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consultant` (
  `id` bigint NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `address` varchar(255) DEFAULT NULL,
  `contact_person` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `specialization` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `consultant_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `courier` (
  `id` bigint NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `address` varchar(255) DEFAULT NULL,
  `contact_person` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `vetted` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `courier_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `department` (
  `id` bigint NOT NULL,
  `managers_group_email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `short_code` varchar(255) DEFAULT NULL,
  `branch_id` bigint DEFAULT NULL,
  `head_of_dept_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbjh7jsn9p35t0809q9ei5ox61` (`branch_id`),
  KEY `FKh7qncmsvgfe3rfb5ki5ky43hm` (`head_of_dept_id`),
  CONSTRAINT `FKbjh7jsn9p35t0809q9ei5ox61` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
  CONSTRAINT `FKh7qncmsvgfe3rfb5ki5ky43hm` FOREIGN KEY (`head_of_dept_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `department_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `district` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grn_commodity` (
  `id` bigint NOT NULL,
  `cost_price` decimal(19,4) DEFAULT NULL,
  `delivered_quantity` int NOT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `ordered_quantity` int NOT NULL,
  `purchase_price` decimal(19,4) DEFAULT NULL,
  `total_cost_price` decimal(19,4) DEFAULT NULL,
  `total_delivered_quantity` int NOT NULL,
  `total_ordered_quantity` int NOT NULL,
  `total_purchase_price` decimal(19,4) DEFAULT NULL,
  `commodity_id` bigint NOT NULL,
  `grn_report_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4f0jqh88igu4qs8oq4yuq8jn3` (`commodity_id`),
  KEY `FK3teooyla3u349e2o02d732iob` (`grn_report_id`),
  CONSTRAINT `FK3teooyla3u349e2o02d732iob` FOREIGN KEY (`grn_report_id`) REFERENCES `grn_report` (`id`),
  CONSTRAINT `FK4f0jqh88igu4qs8oq4yuq8jn3` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grn_commodity_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grn_report` (
  `id` bigint NOT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `document_path` varchar(255) DEFAULT NULL,
  `grn_downloaded` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `stock_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtnh3ugo74jo98jibjdagpl011` (`stock_id`),
  CONSTRAINT `FKtnh3ugo74jo98jibjdagpl011` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grn_report_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `document_path` varchar(255) DEFAULT NULL,
  `grn_downloaded` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `stock_id` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKm279cvtvghfuykn5gq4609t31` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grn_report_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance` (
  `id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `issuer_id` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  `status_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn5t4op0iril1ymi0e6xwtpyyu` (`issuer_id`),
  KEY `FKansfb1hobs29svx1pr9uqktxx` (`request_id`),
  KEY `FKk6ewfwc4xr70mck4s8fiumg5u` (`requester_id`),
  KEY `FK6iwuurthop537xgx219x6m51t` (`status_id`),
  CONSTRAINT `FK6iwuurthop537xgx219x6m51t` FOREIGN KEY (`status_id`) REFERENCES `status` (`id`),
  CONSTRAINT `FKansfb1hobs29svx1pr9uqktxx` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FKk6ewfwc4xr70mck4s8fiumg5u` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKn5t4op0iril1ymi0e6xwtpyyu` FOREIGN KEY (`issuer_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance_approval_record` (
  `id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `issuance_id` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkth4se4j4tmlmekcohqjkeksg` (`issuance_id`),
  KEY `FK9cu37itvcimw3iqb7mpyo7otf` (`request_id`),
  KEY `FK61cvbmyw28n7gibiu4esy09pm` (`user_id`),
  CONSTRAINT `FK61cvbmyw28n7gibiu4esy09pm` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9cu37itvcimw3iqb7mpyo7otf` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FKkth4se4j4tmlmekcohqjkeksg` FOREIGN KEY (`issuance_id`) REFERENCES `issuance` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance_approval_record_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKio78o6a5p2omtkb1fr8216wff` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance_approval_record_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKs88w7qo6jjluyns8mgoe2iiog` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issuance_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issued_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `issuance_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1fn32eowhaluqv7mpyihotx9n` (`asset_id`),
  KEY `FKadfa0ns67ju9lebiqdc6wlp` (`issuance_id`),
  CONSTRAINT `FK1fn32eowhaluqv7mpyihotx9n` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`),
  CONSTRAINT `FKadfa0ns67ju9lebiqdc6wlp` FOREIGN KEY (`issuance_id`) REFERENCES `issuance` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `issued_commodity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `commodity_id` bigint NOT NULL,
  `issuance_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1jlq1n1rwvp4c5ct9sh8grv29` (`commodity_id`),
  KEY `FKee3xt4jdrvvcmoyj808gr9qj1` (`issuance_id`),
  CONSTRAINT `FK1jlq1n1rwvp4c5ct9sh8grv29` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKee3xt4jdrvvcmoyj808gr9qj1` FOREIGN KEY (`issuance_id`) REFERENCES `issuance` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_request` (
  `id` bigint NOT NULL,
  `active` bit(1) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `leave_type` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `acting_user` bigint DEFAULT NULL,
  `on_leave_user` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrytmvi7jnl3p98sn60p7xnmlc` (`acting_user`),
  KEY `FK71bfvonksra464cptr3ynov9e` (`on_leave_user`),
  CONSTRAINT `FK71bfvonksra464cptr3ynov9e` FOREIGN KEY (`on_leave_user`) REFERENCES `users` (`id`),
  CONSTRAINT `FKrytmvi7jnl3p98sn60p7xnmlc` FOREIGN KEY (`acting_user`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `leave_request_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completion_date` datetime(6) DEFAULT NULL,
  `courier_service` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `dispatch_date` datetime(6) DEFAULT NULL,
  `expected_delivery_date` datetime(6) DEFAULT NULL,
  `inventory_settled` bit(1) NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `movement_category` varchar(20) NOT NULL,
  `movement_type` varchar(40) NOT NULL,
  `receipt_date` datetime(6) DEFAULT NULL,
  `receipt_status` varchar(30) DEFAULT 'PENDING',
  `remarks` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'INITIATED',
  `tracking_number` varchar(255) DEFAULT NULL,
  `dest_store_id` bigint DEFAULT NULL,
  `initiator_id` bigint DEFAULT NULL,
  `receiving_officer_id` bigint DEFAULT NULL,
  `recipient_user_id` bigint DEFAULT NULL,
  `repair_id` bigint DEFAULT NULL,
  `request_id` bigint DEFAULT NULL,
  `source_store_id` bigint DEFAULT NULL,
  `approval_tier_index` int NOT NULL,
  `current_approver_id` bigint DEFAULT NULL,
  `consultant_id` bigint DEFAULT NULL,
  `custody_transfer_settled` bit(1) NOT NULL,
  `plate_number` varchar(255) DEFAULT NULL,
  `courier_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7geee2srqcqw4hsfk3dexsofl` (`dest_store_id`),
  KEY `FK9y6nqwrwh6n0xfr6girfc4n15` (`initiator_id`),
  KEY `FKb3nnc2imp27d07l3ih0b254lv` (`receiving_officer_id`),
  KEY `FKiybo6ofsg4e0wjq72v969qny4` (`recipient_user_id`),
  KEY `FKagc3lko1iiobu0aihyxpnbibu` (`repair_id`),
  KEY `FK83jmxudsy3n52b34utl159mej` (`request_id`),
  KEY `FKo8iyrd8pc2133u1ft913oja3r` (`source_store_id`),
  KEY `FKhkp00p4prfbavira00ikiv8b6` (`current_approver_id`),
  KEY `FK479fxnla4toqc3e8f7a2c5ch7` (`consultant_id`),
  KEY `FKo008amu4xubtb4gb2uc51egyq` (`courier_id`),
  CONSTRAINT `FK479fxnla4toqc3e8f7a2c5ch7` FOREIGN KEY (`consultant_id`) REFERENCES `consultant` (`id`),
  CONSTRAINT `FK7geee2srqcqw4hsfk3dexsofl` FOREIGN KEY (`dest_store_id`) REFERENCES `store_container` (`id`),
  CONSTRAINT `FK83jmxudsy3n52b34utl159mej` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FK9y6nqwrwh6n0xfr6girfc4n15` FOREIGN KEY (`initiator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKagc3lko1iiobu0aihyxpnbibu` FOREIGN KEY (`repair_id`) REFERENCES `repair` (`id`),
  CONSTRAINT `FKb3nnc2imp27d07l3ih0b254lv` FOREIGN KEY (`receiving_officer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhkp00p4prfbavira00ikiv8b6` FOREIGN KEY (`current_approver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKiybo6ofsg4e0wjq72v969qny4` FOREIGN KEY (`recipient_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKo008amu4xubtb4gb2uc51egyq` FOREIGN KEY (`courier_id`) REFERENCES `courier` (`id`),
  CONSTRAINT `FKo8iyrd8pc2133u1ft913oja3r` FOREIGN KEY (`source_store_id`) REFERENCES `store_container` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement_approval_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(20) NOT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `tier_index` int NOT NULL,
  `tier_role` varchar(255) DEFAULT NULL,
  `actor_id` bigint DEFAULT NULL,
  `movement_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa4b5adfgdjmfi5ik8p3mxe6p` (`actor_id`),
  KEY `FKcebe4dyqwdjr86tvr9lj1auep` (`movement_id`),
  CONSTRAINT `FKa4b5adfgdjmfi5ik8p3mxe6p` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKcebe4dyqwdjr86tvr9lj1auep` FOREIGN KEY (`movement_id`) REFERENCES `movement` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `completion_date` datetime(6) DEFAULT NULL,
  `courier_service` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `dispatch_date` datetime(6) DEFAULT NULL,
  `expected_delivery_date` datetime(6) DEFAULT NULL,
  `inventory_settled` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `movement_category` varchar(20) NOT NULL,
  `movement_type` varchar(40) NOT NULL,
  `receipt_date` datetime(6) DEFAULT NULL,
  `receipt_status` varchar(30) DEFAULT 'PENDING',
  `remarks` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'INITIATED',
  `tracking_number` varchar(255) DEFAULT NULL,
  `approval_tier_index` int DEFAULT NULL,
  `custody_transfer_settled` bit(1) DEFAULT NULL,
  `plate_number` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FK4yjcism8yqywu8t1boiyer97k` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement_delivery_documents` (
  `movement_id` bigint NOT NULL,
  `document` varchar(255) DEFAULT NULL,
  KEY `FKps0c19hn5eapb20pt82lo5lg8` (`movement_id`),
  CONSTRAINT `FKps0c19hn5eapb20pt82lo5lg8` FOREIGN KEY (`movement_id`) REFERENCES `movement` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_tag` varchar(255) DEFAULT NULL,
  `quantity` int NOT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `asset_id` bigint DEFAULT NULL,
  `commodity_id` bigint DEFAULT NULL,
  `movement_id` bigint NOT NULL,
  `prior_status_code` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsuw7k2xhigmcsop4dr805qaul` (`asset_id`),
  KEY `FKf74f7m8kyojl7t8msah0mngkh` (`commodity_id`),
  KEY `FK2a6jcywl2v7gnp45ucn8p3y93` (`movement_id`),
  CONSTRAINT `FK2a6jcywl2v7gnp45ucn8p3y93` FOREIGN KEY (`movement_id`) REFERENCES `movement` (`id`),
  CONSTRAINT `FKf74f7m8kyojl7t8msah0mngkh` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKsuw7k2xhigmcsop4dr805qaul` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `movement_item_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `asset_tag` varchar(255) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `serial_number` varchar(255) DEFAULT NULL,
  `movement_id` bigint DEFAULT NULL,
  `prior_status_code` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKl9ssj6eib4gbcy2e3c70v6aow` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `permissions` (
  `id` bigint NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `permissions_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `region` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `repair` (
  `id` bigint NOT NULL,
  `completion_notes` varchar(255) DEFAULT NULL,
  `repair_end_date` datetime(6) DEFAULT NULL,
  `repair_reason` varchar(255) DEFAULT NULL,
  `repair_start_date` datetime(6) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `technician` varchar(255) DEFAULT NULL,
  `asset_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKto79k2quiw2bg04lyl3qm9ce4` (`asset_id`),
  CONSTRAINT `FKto79k2quiw2bg04lyl3qm9ce4` FOREIGN KEY (`asset_id`) REFERENCES `asset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `repair_completion_documents` (
  `repair_id` bigint NOT NULL,
  `document` varchar(255) DEFAULT NULL,
  KEY `FK44stvm2xrrfa63sl4exyefykf` (`repair_id`),
  CONSTRAINT `FK44stvm2xrrfa63sl4exyefykf` FOREIGN KEY (`repair_id`) REFERENCES `repair` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `repair_documents` (
  `repair_id` bigint NOT NULL,
  `document` varchar(255) DEFAULT NULL,
  KEY `FKuh9lba5es5niroyix6dkb7mr` (`repair_id`),
  CONSTRAINT `FKuh9lba5es5niroyix6dkb7mr` FOREIGN KEY (`repair_id`) REFERENCES `repair` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `repair_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attributes_json` longtext,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `deletion_date` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_branch_non_stationery` bit(1) DEFAULT NULL,
  `is_branch_stationery` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `priority` varchar(255) DEFAULT NULL,
  `signature_path` varchar(255) DEFAULT NULL,
  `time_of_submission_of_request` datetime(6) DEFAULT NULL,
  `asset_type_id` bigint DEFAULT NULL,
  `current_approver_id` bigint DEFAULT NULL,
  `current_unit_id` bigint DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  `status_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKorfst1lkcef3301anm6txa0jp` (`asset_type_id`),
  KEY `FKhcr0mnx5rn4ery29y3kk3hi8m` (`current_approver_id`),
  KEY `FK800w4a7vg4tgx0pq9088m8c9x` (`current_unit_id`),
  KEY `FKlslgacr7bnkynrkuqcyvy2tn9` (`requester_id`),
  KEY `FK9vq2pk42lwv57luvm5h5yle7y` (`status_id`),
  CONSTRAINT `FK800w4a7vg4tgx0pq9088m8c9x` FOREIGN KEY (`current_unit_id`) REFERENCES `unit` (`id`),
  CONSTRAINT `FK9vq2pk42lwv57luvm5h5yle7y` FOREIGN KEY (`status_id`) REFERENCES `status` (`id`),
  CONSTRAINT `FKhcr0mnx5rn4ery29y3kk3hi8m` FOREIGN KEY (`current_approver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlslgacr7bnkynrkuqcyvy2tn9` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKorfst1lkcef3301anm6txa0jp` FOREIGN KEY (`asset_type_id`) REFERENCES `asset_type` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `attributes_json` longtext,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `deletion_date` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_branch_non_stationery` bit(1) DEFAULT NULL,
  `is_branch_stationery` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `priority` varchar(255) DEFAULT NULL,
  `signature_path` varchar(255) DEFAULT NULL,
  `time_of_submission_of_request` datetime(6) DEFAULT NULL,
  `current_approver_id` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKjjkiffn9whs21k92mhfkqr3da` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_commodity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int NOT NULL,
  `commodity_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9g9015wj259slancxftxfhrjm` (`commodity_id`),
  KEY `FKj1w3dxc5e89335e2olxsv003` (`request_id`),
  CONSTRAINT `FK9g9015wj259slancxftxfhrjm` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKj1w3dxc5e89335e2olxsv003` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `approver_id` bigint NOT NULL,
  `request_id` bigint NOT NULL,
  `status_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKehug63btpy1kjslyamfim32j5` (`approver_id`),
  KEY `FKlqtt9in385vntbfmwv71d6xdt` (`request_id`),
  KEY `FKn62tgbpj7stejvox8w7s8nl4u` (`status_id`),
  CONSTRAINT `FKehug63btpy1kjslyamfim32j5` FOREIGN KEY (`approver_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlqtt9in385vntbfmwv71d6xdt` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FKn62tgbpj7stejvox8w7s8nl4u` FOREIGN KEY (`status_id`) REFERENCES `status` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_report_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FK7m9lry0pfnbj47jhr2iwm75cr` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_step_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('PENDING','APPROVED','REJECTED','ISSUED','ACKNOWLEDGED','SUPERSEDED') NOT NULL,
  `actor_email` varchar(255) DEFAULT NULL,
  `comment` text,
  `create_date` datetime(6) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_reminder_at` datetime(6) DEFAULT NULL,
  `step_name` varchar(255) DEFAULT NULL,
  `step_order` int NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `instance_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaxi8pdfrmqru6dn72is6sytnx` (`actor_id`),
  KEY `FKm4eqynpy3y27vllocb4tco30q` (`instance_id`),
  CONSTRAINT `FKaxi8pdfrmqru6dn72is6sytnx` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKm4eqynpy3y27vllocb4tco30q` FOREIGN KEY (`instance_id`) REFERENCES `request_workflow_instance` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `request_workflow_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_date` datetime(6) DEFAULT NULL,
  `current_step_order` int NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `status` enum('IN_PROGRESS','COMPLETED','REJECTED','SUPERSEDED') NOT NULL,
  `request_id` bigint NOT NULL,
  `workflow_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6w4i5u0a2xv80y2j59ulaj2kl` (`request_id`),
  KEY `FKbtpjj8w16c203bl3abdg0a35y` (`workflow_id`),
  CONSTRAINT `FK6w4i5u0a2xv80y2j59ulaj2kl` FOREIGN KEY (`request_id`) REFERENCES `request` (`id`),
  CONSTRAINT `FKbtpjj8w16c203bl3abdg0a35y` FOREIGN KEY (`workflow_id`) REFERENCES `approval_workflow` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `revinfo` (
  `rev` int NOT NULL AUTO_INCREMENT,
  `revtstmp` bigint DEFAULT NULL,
  PRIMARY KEY (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `FK2xn8qv4vw30i04xdxrpvn3bdi` (`permission_id`),
  CONSTRAINT `FK2xn8qv4vw30i04xdxrpvn3bdi` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`),
  CONSTRAINT `FKtfgq8q9blrp0pt1pvggyli3v9` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles` (
  `id` bigint NOT NULL,
  `create_date` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ofx66keruapi6vyqpv6f2or37` (`name`),
  UNIQUE KEY `UKofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `FKt0mnl3rej2p0h9gxnbalf2kdd` (`rev`),
  CONSTRAINT `FKt0mnl3rej2p0h9gxnbalf2kdd` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `roles_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `status` (
  `id` bigint NOT NULL,
  `code` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `status_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock` (
  `id` bigint NOT NULL,
  `balance_cost` decimal(19,4) DEFAULT NULL,
  `can_not_be_deleted` bit(1) NOT NULL,
  `create_date` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `grn_number` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `lpo_number` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `reference_number` varchar(255) DEFAULT NULL,
  `total_cost` decimal(19,4) DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `status_id` bigint DEFAULT NULL,
  `supplier_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8wkpxnja0ikk6t0xp3ju8aoar` (`branch_id`),
  KEY `FK3cqvoj87nxtyypkdxj01b38in` (`status_id`),
  KEY `FKl2fbubw80ojtirp051oms6piu` (`supplier_id`),
  CONSTRAINT `FK3cqvoj87nxtyypkdxj01b38in` FOREIGN KEY (`status_id`) REFERENCES `status` (`id`),
  CONSTRAINT `FK8wkpxnja0ikk6t0xp3ju8aoar` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
  CONSTRAINT `FKl2fbubw80ojtirp051oms6piu` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `balance_cost` decimal(19,4) DEFAULT NULL,
  `can_not_be_deleted` bit(1) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `disposal_date` datetime(6) DEFAULT NULL,
  `disposed` bit(1) DEFAULT NULL,
  `grn_number` varchar(255) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `lpo_number` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `reference_number` varchar(255) DEFAULT NULL,
  `total_cost` decimal(19,4) DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FK9jr42w4706d7tp71r0n8us4v` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_commodity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cost_price` decimal(19,4) DEFAULT NULL,
  `delivered_quantity` int NOT NULL,
  `ordered_quantity` int NOT NULL,
  `purchase_price` decimal(19,4) DEFAULT NULL,
  `registered_number` int NOT NULL,
  `commodity_id` bigint NOT NULL,
  `stock_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9dq8sbwdxvqfwjs6pwcb6c0u5` (`commodity_id`),
  KEY `FKcuygegfpnufe6hv399b1t700d` (`stock_id`),
  CONSTRAINT `FK9dq8sbwdxvqfwjs6pwcb6c0u5` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKcuygegfpnufe6hv399b1t700d` FOREIGN KEY (`stock_id`) REFERENCES `stock` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store` (
  `id` bigint NOT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `quantity` int NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `commodity_id` bigint NOT NULL,
  `store_id` bigint DEFAULT NULL,
  `min_level` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKp3egiwwxfeysf52nr7xsm5akd` (`branch_id`),
  KEY `FKlrsmy2bxn76uhdnqa6xa22u70` (`store_id`),
  CONSTRAINT `FKkyg0tf5d56bena7yicgr9couv` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`),
  CONSTRAINT `FKlrsmy2bxn76uhdnqa6xa22u70` FOREIGN KEY (`store_id`) REFERENCES `store_container` (`id`),
  CONSTRAINT `FKp3egiwwxfeysf52nr7xsm5akd` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `min_level` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKt2lr04608p8l6g6cpsvym2jkg` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_container` (
  `id` bigint NOT NULL,
  `active` bit(1) NOT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `store_type` varchar(20) NOT NULL DEFAULT 'ADMIN',
  `department_id` bigint DEFAULT NULL,
  `location_id` bigint DEFAULT NULL,
  `courier_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_store_location_type_department` (`location_id`,`store_type`,`department_id`),
  KEY `FK2ghwd7glrs0ajy7c4pcnbadsd` (`department_id`),
  KEY `FK9j2vdvus37hgulimq5xs76d4s` (`courier_id`),
  CONSTRAINT `FK2ghwd7glrs0ajy7c4pcnbadsd` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FK9j2vdvus37hgulimq5xs76d4s` FOREIGN KEY (`courier_id`) REFERENCES `courier` (`id`),
  CONSTRAINT `FKotcf139o7hptb1ejixijup5e` FOREIGN KEY (`location_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_container_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `active` bit(1) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `store_type` varchar(20) NOT NULL DEFAULT 'ADMIN',
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKf62hfdicbjvhcq86lvr8hd17a` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_container_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `store_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `supplier` (
  `id` bigint NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `telephone` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `supplier_commodity` (
  `supplier_id` bigint NOT NULL,
  `commodity_id` bigint NOT NULL,
  PRIMARY KEY (`supplier_id`,`commodity_id`),
  KEY `FKrdjb09fmgxf4cgypaklakrx2t` (`commodity_id`),
  CONSTRAINT `FKhxfgkm18ud7rujtmicyk0iwe0` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
  CONSTRAINT `FKrdjb09fmgxf4cgypaklakrx2t` FOREIGN KEY (`commodity_id`) REFERENCES `commodity` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `supplier_seq` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `title` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `short_code` varchar(255) DEFAULT NULL,
  `branch_id` bigint DEFAULT NULL,
  `reports_to_id` bigint DEFAULT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkgxrt0i6sgbbhseucra3cb3vl` (`branch_id`),
  KEY `FKiwm7k5j8bq1flgrbvh5ynhmct` (`reports_to_id`),
  KEY `FK12lus4bxrurhdxae1imk5pdqg` (`role_id`),
  CONSTRAINT `FK12lus4bxrurhdxae1imk5pdqg` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKiwm7k5j8bq1flgrbvh5ynhmct` FOREIGN KEY (`reports_to_id`) REFERENCES `title` (`id`),
  CONSTRAINT `FKkgxrt0i6sgbbhseucra3cb3vl` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expired` bit(1) NOT NULL,
  `revoked` bit(1) NOT NULL,
  `token` varchar(255) DEFAULT NULL,
  `token_type` enum('BEARER') DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKj8rfw4x0wjjyibfqq566j4qng` (`user_id`),
  CONSTRAINT `FKj8rfw4x0wjjyibfqq566j4qng` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `unit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1552sluc3b0qfy446wfesxixq` (`department_id`),
  CONSTRAINT `FK1552sluc3b0qfy446wfesxixq` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKt7e7djp752sqn6w22i6ocqy6q` (`role_id`),
  CONSTRAINT `FKj345gk1bovqvfame88rcx7yyx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt7e7djp752sqn6w22i6ocqy6q` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `availability` varchar(255) DEFAULT NULL,
  `blocked` bit(1) NOT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `failed_login_attempts` int NOT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `is_account_non_locked` bit(1) NOT NULL,
  `is_acting` bit(1) NOT NULL,
  `is_enabled` bit(1) NOT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `other_name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `profile_image` varchar(255) DEFAULT NULL,
  `staff_number` varchar(255) DEFAULT NULL,
  `acting_title_id` bigint DEFAULT NULL,
  `branch_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `title_id` bigint DEFAULT NULL,
  `unit_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  KEY `FKfi5tonlktw0h3lj117unprlcw` (`acting_title_id`),
  KEY `FKixo09sv3j1j6hfox3cx6d2ggg` (`branch_id`),
  KEY `FKfi832e3qv89fq376fuh8920y4` (`department_id`),
  KEY `FKbkodvdicj8ip4804wiqhe9e9d` (`title_id`),
  KEY `FKm96i7qxrcph7thq7llp4svj03` (`unit_id`),
  CONSTRAINT `FKbkodvdicj8ip4804wiqhe9e9d` FOREIGN KEY (`title_id`) REFERENCES `title` (`id`),
  CONSTRAINT `FKfi5tonlktw0h3lj117unprlcw` FOREIGN KEY (`acting_title_id`) REFERENCES `title` (`id`),
  CONSTRAINT `FKfi832e3qv89fq376fuh8920y4` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`),
  CONSTRAINT `FKixo09sv3j1j6hfox3cx6d2ggg` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
  CONSTRAINT `FKm96i7qxrcph7thq7llp4svj03` FOREIGN KEY (`unit_id`) REFERENCES `unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users_aud` (
  `id` bigint NOT NULL,
  `rev` int NOT NULL,
  `revtype` tinyint DEFAULT NULL,
  `availability` varchar(255) DEFAULT NULL,
  `blocked` bit(1) DEFAULT NULL,
  `create_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `failed_login_attempts` int DEFAULT NULL,
  `first_name` varchar(255) DEFAULT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `is_acting` bit(1) DEFAULT NULL,
  `last_modified` datetime(6) DEFAULT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `other_name` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `profile_image` varchar(255) DEFAULT NULL,
  `staff_number` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`rev`,`id`),
  CONSTRAINT `FKc4vk4tui2la36415jpgm9leoq` FOREIGN KEY (`rev`) REFERENCES `revinfo` (`rev`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Hibernate's id generators, primed.
--
-- Every entity here is `@GeneratedValue(strategy = AUTO)`, which on MySQL -- a server with
-- no native sequences -- Hibernate emulates with a one-row `<table>_seq` table holding the
-- next high value. When Hibernate creates that table itself it also inserts the starting
-- row; a schema dumped with --no-data brings the table and not the row, and the first
-- insert then fails with "could not read a hi value - you need to populate the table".
--
-- This is schema infrastructure rather than business data, which is why it belongs in a
-- migration and not in a seeder. Found by booting the application against an empty
-- database: the migrations all succeeded and `DataInitializer` then failed on the very
-- first permission it tried to write.
INSERT INTO `acknowledge_issuance_receipt_seq` (next_val) VALUES (1);
INSERT INTO `acknowledge_request_receipt_seq` (next_val) VALUES (1);
INSERT INTO `asset_assignment_history_seq` (next_val) VALUES (1);
INSERT INTO `asset_seq` (next_val) VALUES (1);
INSERT INTO `asset_type_seq` (next_val) VALUES (1);
INSERT INTO `consultant_seq` (next_val) VALUES (1);
INSERT INTO `courier_seq` (next_val) VALUES (1);
INSERT INTO `department_seq` (next_val) VALUES (1);
INSERT INTO `grn_commodity_seq` (next_val) VALUES (1);
INSERT INTO `grn_report_seq` (next_val) VALUES (1);
INSERT INTO `issuance_approval_record_seq` (next_val) VALUES (1);
INSERT INTO `issuance_seq` (next_val) VALUES (1);
INSERT INTO `leave_request_seq` (next_val) VALUES (1);
INSERT INTO `password_reset_seq` (next_val) VALUES (1);
INSERT INTO `permissions_seq` (next_val) VALUES (1);
INSERT INTO `repair_seq` (next_val) VALUES (1);
INSERT INTO `roles_seq` (next_val) VALUES (1);
INSERT INTO `status_seq` (next_val) VALUES (1);
INSERT INTO `stock_seq` (next_val) VALUES (1);
INSERT INTO `store_container_seq` (next_val) VALUES (1);
INSERT INTO `store_seq` (next_val) VALUES (1);
INSERT INTO `supplier_seq` (next_val) VALUES (1);

SET FOREIGN_KEY_CHECKS = 1;
