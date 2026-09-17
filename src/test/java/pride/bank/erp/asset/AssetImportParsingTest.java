package pride.bank.erp.asset;

import org.junit.jupiter.api.Test;
import pride.bank.erp.exception.BadRequestException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the tolerances the asset importer depends on.
 *
 * <p>These matter more than they look. The importer's promise is that one bad cell costs one row and
 * is reported against that row's number — which only holds if every parser here throws
 * {@link BadRequestException} rather than something the row loop would let escape, and if the shapes
 * a real migration file actually contains are accepted rather than rejected wholesale.
 *
 * <p>The previous importer bound the date column as an {@code Integer} Excel serial. A user typing
 * a date failed Jackson's binding before any row was attempted, so a two-thousand-row file was
 * rejected in full because of one cell. That is the failure these guard against.
 */
class AssetImportParsingTest {

    // ── Dates ────────────────────────────────────────────────────────────────

    @Test
    void readsTheDateShapesASpreadsheetActuallyContains() {
        // A date-formatted cell reaches the browser as an Excel serial.
        assertThat(AssetImportParsing.parseDate("44927", "Date Received")).isEqualTo("2023-01-01");
        // And the several ways people type one.
        assertThat(AssetImportParsing.parseDate("01/03/2023", "Date Received")).isEqualTo("2023-03-01");
        assertThat(AssetImportParsing.parseDate("1/3/2023", "Date Received")).isEqualTo("2023-03-01");
        assertThat(AssetImportParsing.parseDate("2023-03-01", "Date Received")).isEqualTo("2023-03-01");
        assertThat(AssetImportParsing.parseDate("01-Mar-2023", "Date Received")).isEqualTo("2023-03-01");
        // Some exports append a midnight time.
        assertThat(AssetImportParsing.parseDate("2023-03-01T00:00:00", "Date Received")).isEqualTo("2023-03-01");
    }

    /** dd/mm, not mm/dd: 03/01 is 3 January here, and reading it as 1 March would be silent and wrong. */
    @Test
    void readsDatesAsDayFirst() {
        assertThat(AssetImportParsing.parseDate("03/01/2023", "Date Received")).isEqualTo("2023-01-03");
    }

    @Test
    void blankDateIsNotAnError() {
        assertThat(AssetImportParsing.parseDate(null, "Date Received")).isNull();
        assertThat(AssetImportParsing.parseDate("   ", "Date Received")).isNull();
    }

    /**
     * A bare year is far likelier to be someone typing "2019" than Excel day 2019, which is 1905.
     * Accepting it would file assets five years before the bank's oldest record and say nothing.
     */
    @Test
    void rejectsABareYearRatherThanReadingItAsASerial() {
        assertThatThrownBy(() -> AssetImportParsing.parseDate("2019", "Date Received"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Date Received");
    }

    @Test
    void unreadableDateNamesTheColumnAndTheValue() {
        assertThatThrownBy(() -> AssetImportParsing.parseDate("last March", "Date Received"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last March")
                .hasMessageContaining("dd/mm/yyyy");
    }

    // ── Money ────────────────────────────────────────────────────────────────

    @Test
    void stripsTheDecorationPeoplePutInMoneyCells() {
        assertThat(AssetImportParsing.parseMoney("UGX 1,200,000", "Purchase Cost"))
                .isEqualByComparingTo(new BigDecimal("1200000"));
        assertThat(AssetImportParsing.parseMoney("  2 500.50 ", "Purchase Cost"))
                .isEqualByComparingTo(new BigDecimal("2500.50"));
        assertThat(AssetImportParsing.parseMoney("(400)", "Purchase Cost"))
                .isEqualByComparingTo(new BigDecimal("-400"));
    }

    /**
     * Blank is null, not zero. "We do not know what this cost" and "this cost nothing" are different
     * facts, and a migration is full of the first — recording them as the second would put a
     * fabricated zero into the asset register's valuation.
     */
    @Test
    void blankMoneyIsUnknownNotZero() {
        assertThat(AssetImportParsing.parseMoney(null, "Purchase Cost")).isNull();
        assertThat(AssetImportParsing.parseMoney("  ", "Purchase Cost")).isNull();
    }

    @Test
    void unreadableMoneyNamesTheColumnAndTheValue() {
        assertThatThrownBy(() -> AssetImportParsing.parseMoney("about 2m", "Purchase Cost"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Purchase Cost")
                .hasMessageContaining("about 2m");
    }

    // ── Network identifiers ──────────────────────────────────────────────────

    /** Normalised so two records of the same machine are actually comparable. */
    @Test
    void normalisesMacAddressesToOneForm() {
        String expected = "00:1B:44:11:3A:B7";
        assertThat(AssetImportParsing.parseMacAddress("00-1B-44-11-3A-B7")).isEqualTo(expected);
        assertThat(AssetImportParsing.parseMacAddress("001b44113ab7")).isEqualTo(expected);
        assertThat(AssetImportParsing.parseMacAddress("00:1b:44:11:3a:b7")).isEqualTo(expected);
    }

    @Test
    void rejectsThingsThatAreNotAddresses() {
        assertThatThrownBy(() -> AssetImportParsing.parseMacAddress("JHK4LM3"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AssetImportParsing.parseIpAddress("10.20.1.999"))
                .isInstanceOf(BadRequestException.class);
        assertThat(AssetImportParsing.parseIpAddress("10.20.1.44")).isEqualTo("10.20.1.44");
        assertThat(AssetImportParsing.parseIpAddress(null)).isNull();
    }

    // ── The status allow-list ────────────────────────────────────────────────

    /**
     * The status table is shared with requests, stock and the approval ladders. Only asset-lifecycle
     * states may be imported — filing a laptop as "BOM Approved" would leave it somewhere no screen
     * knows how to move it out of.
     */
    @Test
    void onlyAssetLifecycleStatusesAreImportable() {
        assertThat(AssetImportStatuses.isImportable("issuanceAvailable")).isTrue();
        assertThat(AssetImportStatuses.isImportable("inMaintenance")).isTrue();

        assertThat(AssetImportStatuses.isImportable("bomApproved")).isFalse();
        assertThat(AssetImportStatuses.isImportable("stockClosedShort")).isFalse();
        assertThat(AssetImportStatuses.isImportable("requestApproved")).isFalse();
        assertThat(AssetImportStatuses.isImportable(null)).isFalse();
    }

    /** Blank Status must land on a real, importable state rather than nothing. */
    @Test
    void theBlankStatusDefaultIsItselfImportable() {
        assertThat(AssetImportStatuses.isImportable(AssetImportStatuses.DEFAULT)).isTrue();
    }
}
