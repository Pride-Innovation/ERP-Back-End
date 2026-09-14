/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exportconfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Which columns each table puts into its exported file.
 *
 * <h2>What this is for</h2>
 * Tidiness. A register that runs to eighteen columns prints as an unreadable wall; somebody with the
 * permission decides which six belong in the file, and everyone's export follows. Every column stays
 * visible on screen either way, and the file is still assembled in the browser — so this is a
 * formatting decision, not a disclosure one.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportColumnConfigService {

    private final ExportColumnConfigRepository repository;
    private final AuditService auditService;

    /** A table may not be configured down to nothing — an export of no columns is a broken export. */
    private static final int MIN_COLUMNS = 1;

    /**
     * Every table's configured columns, keyed by table.
     *
     * <p>A table absent from this map is <b>unconfigured</b>, and the client exports all of its
     * columns. That is the widest answer and it is the intended one: this arrives against twenty-odd
     * existing tables that nobody has set up yet, and the alternative is that the day it ships every
     * export in the bank comes out blank.
     *
     * <p>So absence and emptiness must not be the same state, and they are not — a configured table
     * always has at least one row ({@link #MIN_COLUMNS}), so an empty list can never be stored and
     * "not in the map" unambiguously means "not configured". The trap being avoided is the one
     * recorded elsewhere in this codebase, where an empty "allowed" list silently meant *everything*
     * because nobody decided which way round it should read.
     */
    public Map<String, List<String>> fetchAll() {
        Map<String, List<String>> byTable = new LinkedHashMap<>();
        for (ExportColumnConfig row : repository.findAllByOrderByTableKeyAscSortOrderAsc()) {
            byTable.computeIfAbsent(row.getTableKey(), key -> new ArrayList<>()).add(row.getColumnKey());
        }
        return byTable;
    }

    /**
     * Replaces one table's configuration with exactly the columns given, in the order given.
     *
     * <p>A replacement rather than an add/remove pair, for the reason {@code PUT /units/{id}/roles}
     * is one: two operations means one of them eventually gets forgotten, and a column that was meant
     * to be dropped stays in every printed report until somebody notices.
     *
     * @param columnKeys the columns to export, in file order; duplicates are collapsed, order kept
     * @throws BadRequestException if no columns are given — see {@link #MIN_COLUMNS}
     */
    @Transactional
    public List<String> replace(String tableKey, List<String> columnKeys) {
        if (tableKey == null || tableKey.isBlank()) {
            throw new BadRequestException("A table must be named.");
        }

        // LinkedHashSet: the caller's order is the file's order, and a column listed twice is a
        // slip rather than a request for two identical columns.
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        if (columnKeys != null) {
            for (String key : columnKeys) {
                if (key != null && !key.isBlank()) wanted.add(key.trim());
            }
        }

        if (wanted.size() < MIN_COLUMNS) {
            throw new BadRequestException(
                    "Choose at least one column to export. A table configured to export nothing "
                            + "produces an empty file, which reads as a broken report rather than a "
                            + "setting — to stop exporting a table, turn its export off instead.");
        }

        List<String> before = fetchAll().getOrDefault(tableKey, List.of());

        repository.deleteByTableKey(tableKey);
        // Flushed by delete-then-insert inside one transaction; the unique constraint on
        // (table_key, column_key) is what stops a half-applied write leaving a column listed twice.
        repository.flush();

        int order = 0;
        List<ExportColumnConfig> rows = new ArrayList<>();
        for (String columnKey : wanted) {
            rows.add(ExportColumnConfig.builder()
                    .tableKey(tableKey)
                    .columnKey(columnKey)
                    .sortOrder(order++)
                    .build());
        }
        repository.saveAll(rows);

        auditService.record(
                AuditEventType.UPDATED,
                AuditModule.SYSTEM,
                "EXPORT_COLUMNS_CHANGED",
                describeChange(tableKey, before, List.copyOf(wanted)),
                AuditSeverity.INFO,
                "ExportColumnConfig", null);

        log.info("Export columns for '{}' set to {}", tableKey, wanted);
        return List.copyOf(wanted);
    }

    /**
     * Removes a table's configuration, putting it back to exporting every column it has.
     *
     * <p>Distinct from configuring it down to a minimum: this is "stop curating this table", and the
     * absence it leaves behind is exactly the unconfigured state {@link #fetchAll()} describes.
     */
    @Transactional
    public void reset(String tableKey) {
        List<String> before = fetchAll().getOrDefault(tableKey, List.of());
        if (before.isEmpty()) return;

        repository.deleteByTableKey(tableKey);
        auditService.record(
                AuditEventType.UPDATED,
                AuditModule.SYSTEM,
                "EXPORT_COLUMNS_RESET",
                "Export columns for '" + tableKey + "' reset to all columns (was: "
                        + String.join(", ", before) + ")",
                AuditSeverity.INFO,
                "ExportColumnConfig", null);
    }

    /** Says what actually changed, so the trail is readable without diffing two lists by eye. */
    private String describeChange(String tableKey, List<String> before, List<String> after) {
        if (before.isEmpty()) {
            return "Export columns for '" + tableKey + "' set to: " + String.join(", ", after)
                    + " (previously unconfigured — all columns were exported)";
        }
        if (before.equals(after)) {
            return "Export columns for '" + tableKey + "' saved unchanged: " + String.join(", ", after);
        }

        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        List<String> removed = new ArrayList<>(before);
        removed.removeAll(after);

        StringBuilder out = new StringBuilder("Export columns for '").append(tableKey).append("' changed");
        if (!added.isEmpty()) out.append("; added ").append(String.join(", ", added));
        if (!removed.isEmpty()) out.append("; removed ").append(String.join(", ", removed));
        if (added.isEmpty() && removed.isEmpty()) out.append("; reordered");
        return out.append(". Now: ").append(String.join(", ", after)).toString();
    }
}
