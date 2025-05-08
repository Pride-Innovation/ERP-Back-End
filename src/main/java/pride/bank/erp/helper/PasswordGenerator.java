/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.helper;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

import static pride.bank.erp.helper.Constants.*;


@Component
public class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        password.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        password.append(NUMBERS.charAt(RANDOM.nextInt(NUMBERS.length())));
        password.append(SPECIAL_CHARACTERS.charAt(RANDOM.nextInt(SPECIAL_CHARACTERS.length())));

        String allChars = LETTERS + NUMBERS + SPECIAL_CHARACTERS;
        for (int i = 3; i < PASSWORD_LENGTH; i++) {
            password.append(allChars.charAt(RANDOM.nextInt(allChars.length())));
        }

        return shuffleString(password.toString());
    }

    private static String shuffleString(String input) {
        char[] characters = input.toCharArray();
        for (int i = 0; i < characters.length; i++) {
            int randomIndex = RANDOM.nextInt(characters.length);
            char temp = characters[i];
            characters[i] = characters[randomIndex];
            characters[randomIndex] = temp;
        }
        return new String(characters);
    }
}
