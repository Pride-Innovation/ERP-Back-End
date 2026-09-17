package pride.bank.erp.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application can build its own database from nothing.
 *
 * <h2>The failure this closes</h2>
 * For most of this project's life the migrations began at <b>V2</b>, and
 * {@code flyway.baseline-on-migrate} with {@code baseline-version: 1} told Flyway to <em>assume</em>
 * a version-1 schema was already there. On a database Hibernate had built years earlier that
 * assumption held. On an empty one it was false, and the first deployment into a fresh environment
 * failed at startup: V2's guards test for a <b>column</b> ({@code information_schema.COLUMNS}) rather
 * than for the table, so against an empty database the guard reads "column absent", emits
 * {@code ALTER TABLE stock ADD COLUMN po_number ...}, and MySQL answers that {@code stock} does not
 * exist.
 *
 * <p>{@code application.yaml} predicted this in a comment and offered two ways out. The other one —
 * "bootstrap once with ddl-auto=update" — does not work: Flyway runs before Hibernate, and even with
 * Flyway disabled for one startup the migrations from V12 onward would then collide with the schema
 * Hibernate had just created. {@code V1__baseline.sql} is the route that does.
 *
 * <h2>Why this tests the migrations rather than the baseline</h2>
 * V1 was verified once, against the real thing: an empty database was built from V1..V25 and every
 * column, index and foreign key compared with the reference database — <b>818, 277 and 153
 * respectively, all identical</b> — and the application was then booted against it.
 *
 * <p>What that cannot protect is the <b>next</b> migration. One that creates a table V1 already has,
 * or drops a column V1 does not, is invisible on every existing database: it runs there once and
 * succeeds, because none of those databases was built from V1. It fails only in a brand-new
 * environment — the one place nobody exercises before deploying, which is exactly how the original
 * fault survived as long as it did.
 *
 * <p>So this reads the unguarded DDL out of every migration and checks V1 is in the state that DDL
 * expects. Guarded statements — {@code CREATE TABLE IF NOT EXISTS}, and the
 * {@code SET @ddl := (SELECT IF(NOT EXISTS(...)))} form most of these migrations use — need no such
 * agreement, because they no-op either way.
 */
class BaselineMigrationTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Path BASELINE = MIGRATIONS.resolve("V1__baseline.sql");

    // ── V1 itself ────────────────────────────────────────────────────────────

    @Test
    void theBaselineExistsAndIsTheFirstMigration() {
        assertThat(BASELINE)
                .as("without a V1 the application cannot create a database it does not already have")
                .exists();

        List<String> earlier = migrationFiles().stream()
                .map(p -> p.getFileName().toString())
                .filter(n -> versionOf(n) < 1)
                .collect(Collectors.toList());

        assertThat(earlier)
                .as("a migration numbered below V1 would be ignored on every existing database — they "
                        + "are all baselined at version 1 — while running on new ones, so the two "
                        + "would end up with different schemas and nothing would say so")
                .isEmpty();
    }

    /**
     * Every Hibernate sequence table is primed with its starting row.
     *
     * <p>Each entity is {@code @GeneratedValue(strategy = AUTO)}, which on MySQL — a server with no
     * native sequences — Hibernate emulates with a one-row {@code <table>_seq} table holding the next
     * high value. When Hibernate creates that table itself it inserts the starting row too.
     *
     * <p>A schema captured with {@code mysqldump --no-data} brings the table and <b>not</b> the row,
     * and nothing complains until the first insert, which fails with <i>"could not read a hi value -
     * you need to populate the table"</i>. This is the one piece of row data that is genuinely schema
     * rather than business data, and it is why booting the application was part of verifying V1 —
     * every migration succeeded and {@code DataInitializer} then died on the first permission it
     * tried to write.
     */
    @Test
    void everySequenceTableIsPrimed() {
        String baseline = read(BASELINE);

        Set<String> created = new LinkedHashSet<>(
                allMatches(baseline, "CREATE TABLE `(\\w+_seq)`"));
        Set<String> primed = new LinkedHashSet<>(
                allMatches(baseline, "INSERT INTO `(\\w+_seq)` \\(next_val\\)"));

        assertThat(created)
                .as("V1 parsed as having no sequence tables at all, so this assertion is vacuous")
                .isNotEmpty();

        assertThat(created)
                .as("a sequence table with no starting row fails on the first insert into the entity "
                        + "it serves, long after the migration that created it reported success")
                .isSubsetOf(primed);
    }

    /**
     * The two columns V24 drops without a guard.
     *
     * <p>MySQL has no {@code DROP COLUMN IF EXISTS}, so these must be present in V1 or a new database
     * fails at V24. Named here as well as covered by the sweep below, because this is the case most
     * tempting to tidy out of a baseline — they are columns the application no longer uses.
     */
    @Test
    void theBaselineCarriesTheColumnsV24DropsUnguarded() {
        assertThat(baselineColumns().get("app_notification"))
                .as("V24 drops these and MySQL cannot drop a column conditionally, so removing them "
                        + "from V1 breaks every new environment and no existing one")
                .contains("link", "request_id");
    }

    // ── The invariant that matters going forward ─────────────────────────────

    /**
     * Every unguarded statement from V2 onward agrees with V1.
     *
     * <p>Four ways a migration can contradict the baseline, each producing an error only on a
     * database built from it.
     */
    @Test
    void everyUnguardedMigrationAgreesWithTheBaseline() {
        Map<String, Set<String>> columns = baselineColumns();
        Set<String> tables = columns.keySet();
        Set<String> constraints = baselineConstraintNames();

        List<String> problems = new ArrayList<>();

        for (Path file : migrationFiles()) {
            String name = file.getFileName().toString();
            if (versionOf(name) <= 1) continue;

            for (String statement : unguardedStatements(read(file))) {

                Matcher create = Pattern.compile("^CREATE TABLE (?:IF NOT EXISTS )?`?(\\w+)`?",
                        Pattern.CASE_INSENSITIVE).matcher(statement);
                if (create.find()) {
                    if (statement.toUpperCase().contains("IF NOT EXISTS")) continue;
                    if (tables.contains(create.group(1))) {
                        problems.add(name + ": creates table '" + create.group(1)
                                + "' unguarded, but V1 already creates it");
                    }
                    continue;
                }

                Matcher index = Pattern.compile("^CREATE (?:UNIQUE )?INDEX `?(\\w+)`?",
                        Pattern.CASE_INSENSITIVE).matcher(statement);
                if (index.find()) {
                    if (constraints.contains(index.group(1))) {
                        problems.add(name + ": creates index '" + index.group(1)
                                + "' unguarded, but V1 already defines it");
                    }
                    continue;
                }

                Matcher alter = Pattern.compile("^ALTER TABLE `?(\\w+)`?\\s",
                        Pattern.CASE_INSENSITIVE).matcher(statement);
                if (!alter.find()) continue;

                String table = alter.group(1);
                // A table a later migration creates is not V1's business either way.
                if (!tables.contains(table)) continue;

                for (String added : allMatches(statement, "ADD COLUMN `?(\\w+)`?")) {
                    if (columns.get(table).contains(added)) {
                        problems.add(name + ": adds column '" + table + "." + added
                                + "' unguarded, but V1 already has it");
                    }
                }
                for (String dropped : allMatches(statement, "DROP COLUMN `?(\\w+)`?")) {
                    if (!columns.get(table).contains(dropped)) {
                        problems.add(name + ": drops column '" + table + "." + dropped
                                + "' unguarded, but V1 never creates it");
                    }
                }
                for (String constraint : allMatches(statement, "ADD CONSTRAINT `?(\\w+)`?")) {
                    if (constraints.contains(constraint)) {
                        problems.add(name + ": adds constraint '" + constraint
                                + "' unguarded, but V1 already defines it");
                    }
                }
            }
        }

        assertThat(problems)
                .as("each of these runs cleanly on every database that already exists and fails on a "
                        + "brand-new one — either guard the statement, or take the object out of V1")
                .isEmpty();
    }

    /**
     * The baseline stays safe for existing databases only while the configuration ignores it.
     *
     * <p>Flyway skips any migration at or below the baseline version recorded in
     * {@code flyway_schema_history}, and every existing environment carries a BASELINE row at
     * version 1 — which is why adding V1 changed nothing on any of them. Raise
     * {@code baseline-version}, or drop {@code baseline-on-migrate}, and that stops being true: V1
     * would be applied to databases that already hold every table it creates.
     */
    @Test
    void theConfigurationStillIgnoresTheBaselineOnExistingDatabases() {
        String config = read(Path.of("src/main/resources/application.yaml")).replaceAll("\\s+", " ");

        assertThat(config)
                .as("V1 must stay at or below the baseline version, or an existing database will try "
                        + "to run it")
                .contains("baseline-on-migrate: true")
                .contains("baseline-version: 1");
    }

    // ── Reading the files ────────────────────────────────────────────────────

    /**
     * The statements that run exactly as written.
     *
     * <p>Most of these migrations wrap their DDL in
     * {@code SET @ddl := (SELECT IF(NOT EXISTS(...), 'ALTER TABLE ...', 'DO 0'))}, where the DDL is a
     * <em>string literal</em> and runs only if the guard says so. Those lines begin with a quote,
     * which is what separates them here.
     */
    private static List<String> unguardedStatements(String sql) {
        String stripped = Stream.of(sql.split("\n"))
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("--") || trimmed.startsWith("'")) return "";
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .collect(Collectors.joining("\n"));

        return Stream.of(stripped.split(";"))
                .map(s -> s.replaceAll("\\s+", " ").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /** Table to column names, read out of V1's own CREATE TABLE blocks. */
    private static Map<String, Set<String>> baselineColumns() {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        Matcher m = Pattern.compile("CREATE TABLE `(\\w+)` \\((.*?)\\n\\) ", Pattern.DOTALL)
                .matcher(read(BASELINE));
        while (m.find()) {
            Set<String> cols = new LinkedHashSet<>();
            for (String line : m.group(2).split("\n")) {
                Matcher c = Pattern.compile("^\\s*`(\\w+)`\\s").matcher(line);
                if (c.find()) cols.add(c.group(1));
            }
            columns.put(m.group(1), cols);
        }
        assertThat(columns)
                .as("V1 parsed as no tables at all, which would make every check here vacuous")
                .isNotEmpty();
        return columns;
    }

    /** Every named key, unique key and constraint in V1. */
    private static Set<String> baselineConstraintNames() {
        return new LinkedHashSet<>(
                allMatches(read(BASELINE), "(?m)^\\s*(?:UNIQUE KEY|KEY|CONSTRAINT) `(\\w+)`"));
    }

    private static List<String> allMatches(String text, String regex) {
        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) found.add(m.group(1));
        return found;
    }

    private static List<Path> migrationFiles() {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingInt(p -> versionOf(p.getFileName().toString())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new AssertionError("could not list " + MIGRATIONS, e);
        }
    }

    private static int versionOf(String fileName) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(fileName);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
