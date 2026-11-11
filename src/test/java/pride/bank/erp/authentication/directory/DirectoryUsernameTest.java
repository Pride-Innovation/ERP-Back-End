/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.authentication.directory;

import org.junit.jupiter.api.Test;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryUsernameTest {

    @Test
    void testAccountsSignInAsThePersonTheyBelongTo() {
        assertThat(DirectoryUsername.of("sodong@pridebank.co.ug")).isEqualTo("sodong");
        assertThat(DirectoryUsername.of("sodong1@pridebank.co.ug")).isEqualTo("sodong");
        assertThat(DirectoryUsername.of("sodong12@pridebank.co.ug")).isEqualTo("sodong");
    }

    @Test
    void onlyTrailingDigitsComeOffAndCaseDoesNotMatter() {
        assertThat(DirectoryUsername.of("  J2Doe7@PrideBank.co.ug ")).isEqualTo("j2doe");
        assertThat(DirectoryUsername.of("jane.doe@pridemicrofinance.co.ug")).isEqualTo("jane.doe");
    }

    @Test
    void anAddressWithNoUsableNameGivesNone() {
        assertThat(DirectoryUsername.of(null)).isNull();
        assertThat(DirectoryUsername.of("not-an-email")).isNull();
        assertThat(DirectoryUsername.of("123@pridebank.co.ug")).isNull();
    }

    /** One rule for both: an account signs in as the person its mail is delivered to. */
    @Test
    void itAgreesWithTheMailSender() {
        for (String email : new String[]{"sodong12@pridebank.co.ug", "jdoe@pridebank.co.ug", "a.b3@x.ug"}) {
            String mailbox = new ExtractNumberFromEmailAddress(email).getCleanedEmail();
            assertThat(DirectoryUsername.of(email)).isEqualTo(mailbox.substring(0, mailbox.indexOf('@')));
        }
    }
}
