/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.courier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A courier/logistics carrier that can hold custody of stock while an inter-location
 * {@link pride.bank.erp.movement.Movement} is in transit. Either registered ahead of time via
 * Settings ({@code vetted = true}), or auto-created the first time a dispatcher types an
 * unrecognized name at dispatch ({@code vetted = false}) — both get the same custody
 * {@link pride.bank.erp.store.Store} (see {@code CourierService#ensureCustodyStore}), so tracking
 * is uniform regardless of how the courier was registered.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Primary contact person at the courier company. */
    private String contactPerson;

    private String phone;
    private String email;
    private String address;

    /** True for couriers registered ahead of time via Settings; false for ad-hoc, one-off couriers. */
    @Column(name = "vetted", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean vetted = true;

    @Column(name = "active", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean active = true;
}
