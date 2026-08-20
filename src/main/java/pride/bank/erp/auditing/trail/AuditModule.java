/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

/**
 * Which part of the bank an event belongs to.
 *
 * <p>Answers "show me everything that happened in Movement" without the reader having to know which
 * Java package or table that means. The label is what the page shows, so a rename here changes the
 * chip and nothing else.
 */
public enum AuditModule {
    ASSETS("Assets"),
    INVENTORY("Inventory"),
    USERS("Users"),
    STORE("Store"),
    REQUESTS("Requests"),
    MOVEMENT("Movement"),
    DISPOSAL("Disposal"),
    MAINTENANCE("Maintenance"),
    /** Authentication, configuration and anything the application did to itself. */
    SYSTEM("System");

    private final String label;

    AuditModule(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
