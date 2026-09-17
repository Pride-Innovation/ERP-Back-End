package pride.bank.erp.exportconfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pride.bank.erp.exception.BadRequestException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Export columns: a display preference, and the one way it could go badly wrong.
 *
 * <h2>The trap this pins</h2>
 * An unconfigured table must export <b>everything</b>. That is the widest possible answer, and here
 * it is the right one — this feature arrives against twenty-odd existing tables that nobody has set
 * up, and the alternative is that the day it deploys every export in the bank comes out blank.
 *
 * <p>Which makes the distinction between <em>absent</em> and <em>empty</em> load-bearing. If a table
 * could be saved with no columns, "not configured" and "configured to nothing" would be the same
 * state in the data and could only mean one thing — so one of the two behaviours would be wrong,
 * silently, and whichever way it was resolved somebody would get a blank file or an unwanted one.
 *
 * <p>This codebase has already been bitten by the same shape from the other direction, where an
 * empty "allowed ids" list silently meant <em>no restriction</em> rather than <em>nothing
 * allowed</em>. The rule worth carrying: whenever emptiness drives a decision, decide what it means
 * and write the decision down as a test.
 */
@SpringBootTest
class ExportColumnConfigTest {

    private static final String TABLE = "__test_export_table__";

    @Autowired ExportColumnConfigService service;
    @Autowired ExportColumnConfigRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteByTableKey(TABLE);
    }

    /** Absence means "all columns", and it has to be distinguishable from a narrow configuration. */
    @Test
    void anUnconfiguredTableIsAbsentRatherThanEmpty() {
        assertThat(service.fetchAll())
                .as("a table nobody has configured must not appear at all — the client reads its "
                        + "absence as 'export every column', which is what stops this feature "
                        + "blanking every existing export the day it ships")
                .doesNotContainKey(TABLE);

        service.replace(TABLE, List.of("name"));

        assertThat(service.fetchAll())
                .as("and a table configured down to its minimum is still present, so the two states "
                        + "can never be confused for one another")
                .containsEntry(TABLE, List.of("name"));
    }

    /** There is deliberately no way to store the ambiguous state. */
    @Test
    void aTableCannotBeConfiguredToExportNothing() {
        assertThatThrownBy(() -> service.replace(TABLE, List.of()))
                .as("an export of no columns is a broken report, not a preference")
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one column");

        assertThatThrownBy(() -> service.replace(TABLE, null))
                .isInstanceOf(BadRequestException.class);

        assertThat(service.fetchAll()).doesNotContainKey(TABLE);
    }

    /**
     * Saving replaces, so a column removed in the editor leaves the file.
     *
     * <p>An add/remove pair would mean two operations, one of which eventually gets forgotten — and
     * the symptom of forgetting is a column that goes on being printed in every report until someone
     * notices. Same reasoning as {@code PUT /units/{id}/roles}.
     */
    @Test
    void savingReplacesRatherThanMerges() {
        service.replace(TABLE, List.of("name", "branch", "quantity"));
        service.replace(TABLE, List.of("name", "quantity"));

        assertThat(service.fetchAll().get(TABLE))
                .as("'branch' was dropped in the editor, so it must be gone from the file")
                .containsExactly("name", "quantity");
    }

    /** The chosen order is the file's order — not the order rows happen to be built in. */
    @Test
    void theConfiguredOrderIsKept() {
        service.replace(TABLE, List.of("quantity", "name", "branch"));

        assertThat(service.fetchAll().get(TABLE)).containsExactly("quantity", "name", "branch");

        // And it survives a round trip through the repository rather than relying on insertion luck.
        assertThat(repository.findByTableKeyOrderBySortOrderAsc(TABLE))
                .extracting(ExportColumnConfig::getColumnKey)
                .containsExactly("quantity", "name", "branch");
    }

    /** A column listed twice is a slip in the editor, not a request for two identical columns. */
    @Test
    void duplicatesCollapseAndKeepTheirFirstPosition() {
        service.replace(TABLE, List.of("name", "branch", "name"));

        assertThat(service.fetchAll().get(TABLE)).containsExactly("name", "branch");
    }

    /** Resetting restores the unconfigured state, which is different from a minimal one. */
    @Test
    void resettingRestoresAbsenceNotEmptiness() {
        service.replace(TABLE, List.of("name"));
        assertThat(service.fetchAll()).containsKey(TABLE);

        service.reset(TABLE);

        assertThat(service.fetchAll())
                .as("reset means 'stop curating this table', which is absence — the client goes back "
                        + "to exporting every column")
                .doesNotContainKey(TABLE);
    }

    /** Resetting something never configured is a no-op rather than a failure. */
    @Test
    void resettingAnUnconfiguredTableIsHarmless() {
        service.reset(TABLE);
        assertThat(service.fetchAll()).doesNotContainKey(TABLE);
    }
}
