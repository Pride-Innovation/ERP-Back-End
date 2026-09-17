package pride.bank.erp.passwordReset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A password reset must work more than once, and must not keep a copy of the credential.
 *
 * <h2>The bug</h2>
 * Resetting used to insert a row into {@code password_reset} as well as setting the password.
 * {@code PasswordReset} maps {@code user} as {@code @OneToOne}, so Hibernate makes {@code user_id}
 * unique — the first reset for a user succeeded and <b>every later one failed</b> with a duplicate
 * key naming their id. Anyone who had ever reset their password could never do it again, and the
 * error said only "Duplicate entry", which reads as a database fault rather than as a feature that
 * only works once.
 *
 * <h2>Why the row was removed rather than made to upsert</h2>
 * Nothing read it. The repository declares no query methods and nothing outside its own package
 * referenced it, so the table was written on every reset and consumed by nobody. What it stored was a
 * second bcrypt hash of the user's current password and the reset JWT — a duplicated credential and a
 * replayable token, kept permanently, for no purpose. Making it upsert would have fixed the error and
 * kept the liability.
 *
 * <p>Knowing that a password changed, and when, is a real need and is met by the audit trail, which
 * records it as CRITICAL without storing anything secret.
 */
class PasswordResetTest {

    private static final String SERVICE =
            "src/main/java/pride/bank/erp/passwordReset/PasswordResetService.java";

    /** The reset path must not write a credential anywhere. */
    @Test
    void resettingDoesNotStoreACopyOfThePassword() {
        String source = read(SERVICE);

        assertThat(source)
                .as("a second bcrypt hash of the live password, kept permanently and read by nobody, "
                        + "only widens what a database disclosure costs")
                .doesNotContain("passwordResetRepository");

        assertThat(source)
                .as("the row that broke the feature must not come back under another name")
                .doesNotContain("PasswordReset.builder()");
    }

    /**
     * The reset must still be recorded — removing the row must not remove the trail.
     *
     * <p>A completed password change is the point at which control of an account can pass to somebody
     * else, so it is the one thing here that genuinely must be written down.
     */
    @Test
    void aCompletedResetIsStillAudited() {
        assertThat(read(SERVICE))
                .as("if a reset was not expected, this is the row that says when")
                .contains("PASSWORD_RESET_COMPLETED")
                .contains("AuditSeverity.CRITICAL");
    }

    /**
     * The entity and its table are gone, and the migration that drops them is present.
     *
     * <p>Pinned by absence because the broken form reads as harmless: an entity called
     * {@code PasswordReset} sitting beside a reset service is exactly what someone adds a write to.
     * Its {@code @OneToOne} mapping is what made the table hold one row per user, so reinstating it
     * would reinstate the duplicate-key failure along with the stored credential.
     */
    @Test
    void theEntityAndItsTableAreGone() {
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/pride/bank/erp/passwordReset/PasswordReset.java")))
                .as("the entity is unused; leaving it invites a write against a table that stores "
                        + "credentials and permits one row per user")
                .isFalse();

        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/pride/bank/erp/passwordReset/PasswordResetRepository.java")))
                .as("and its repository, which declared no query methods because nothing read it")
                .isFalse();

        assertThat(read("src/main/resources/db/migration/V21__drop_password_reset.sql"))
                .as("the rows already written are duplicated credential hashes and replayable reset "
                        + "tokens; the fix is not complete while they are still there")
                .contains("DROP TABLE IF EXISTS password_reset");
    }

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
