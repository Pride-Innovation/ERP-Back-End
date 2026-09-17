/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exportconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One column that an exportable table puts into its PDF / Excel file.
 *
 * <h2>Presentation, not access control</h2>
 * Every column named here is already on screen and stays on screen, and the file is still built in
 * the browser from rows it already holds. This decides only what goes <em>into the file</em>, so a
 * register running to eighteen columns can be printed as the six anyone actually reads. Nothing here
 * withholds anything from anybody — treating it as though it did is the one misreading that would
 * matter, because it would put a confidentiality expectation on a control that cannot carry one.
 *
 * <h2>Why this is persisted at all</h2>
 * Because one person sets it <b>for everyone</b>. A purely per-viewer choice needs no server, and
 * then the permission to change it would be decorative — its holder would tick columns and nobody
 * else's export would change. Individual users still narrow their own download further, and
 * <em>that</em> half is genuinely local and never reaches here.
 *
 * <p>Not {@code @Audited}: these rows are a display preference that gets rewritten whenever somebody
 * tidies a report, and a field-level history of them would be noise. The act of changing them is
 * recorded in {@code audit_event} instead, which is where the question "who changed this and when"
 * is actually asked.
 */
@Entity
@Table(
        name = "export_column_config",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_export_column",
                columnNames = {"table_key", "column_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportColumnConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The stable identity of a table, from the frontend's export registry.
     *
     * <p><b>Deliberately not the {@code module} string those tables already pass around.</b> That
     * string cannot identify a table: the assets page passes the *asset category name*, so one table
     * arrives under twelve different names, and both the asset-request tabs and the fleet-requisition
     * tabs call themselves {@code "request"}, so two unrelated tables arrive under one. A config
     * keyed on it would fragment one table across twelve entries and merge two others into one.
     */
    @Column(name = "table_key", nullable = false, length = 100)
    private String tableKey;

    /** The row field this column reads — e.g. {@code engravedNumber}. Matches the registry's key. */
    @Column(name = "column_key", nullable = false, length = 100)
    private String columnKey;

    /**
     * Position in the exported file.
     *
     * <p>So a configured set has a deliberate reading order rather than whatever order the row object
     * happened to be built in — which, for the tables whose columns are derived from row keys, is an
     * implementation detail of a mapper nobody was thinking about when they wrote it.
     */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
