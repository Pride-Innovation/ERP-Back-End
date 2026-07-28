/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for creating or updating a department-level store. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoreAdminDTO {

    /** The branch the store sits at. Required on create. */
    private Long locationId;

    /**
     * The department that operates the store. Required — a location-level store already exists for
     * every branch (its ADMIN store) and is created by the seeder, not by hand.
     */
    private Long departmentId;

    /** Display name; defaults to "<Branch> — <Department> Store". */
    private String name;

    /** Deactivate rather than delete, so historical movements keep resolving. */
    private Boolean active;
}
