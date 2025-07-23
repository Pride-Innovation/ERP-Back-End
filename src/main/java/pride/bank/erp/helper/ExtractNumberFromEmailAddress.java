package pride.bank.erp.helper;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ExtractNumberFromEmailAddress {
    private final String originalEmail;
    private final String cleanedEmail;

    public ExtractNumberFromEmailAddress(String email) {
        this.originalEmail = email;
        this.cleanedEmail = cleanEmail(email);
    }

    private String cleanEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        String localPart = parts[0];
        String domainPart = parts[1];

        String cleanedLocal = localPart.replaceAll("\\d+$", "");
        return cleanedLocal + "@" + domainPart;
    }
}
