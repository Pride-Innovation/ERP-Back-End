/*
13.9 Pride's Standard Copyright Notice:
Copyright (c)20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

/**
 * A person, as a picker needs them: enough to choose between two colleagues, and nothing else.
 *
 * <h2>Why not {@code UserResponseDTO}</h2>
 * That is the <b>staff directory's</b> shape - duty station, department, reporting line, staff
 * number, account state, roles, audit columns - because that is what a directory is for. A dropdown
 * saying "assign this to..." needs a name to read, a job title to tell two people called Okello
 * apart, and an id to send.
 *
 * <p>The distinction carries weight here because the endpoint behind this answers to
 * <b>authentication alone</b>. Reusing the directory's shape would have meant every signed-in user
 * could enumerate their branch's full personnel records; this way the widening is confined to what a
 * picker visibly shows anyway.
 *
 * <p>{@code email} is included deliberately: two people do share a name, and it is the work address
 * already printed beside them in these dropdowns today.
 */
public record StaffOptionDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String title) {

    public static StaffOptionDTO from(User user) {
        return new StaffOptionDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getTitle() == null ? null : user.getTitle().getName());
    }
}
