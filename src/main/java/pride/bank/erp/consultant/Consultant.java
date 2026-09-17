/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.consultant;

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
 * An external repair vendor/consultant an asset can be sent to when its category's repair routing is
 * {@code EXTERNAL} (see {@link pride.bank.erp.assetType.RepairDestination}). Referenced by repair
 * movements for accountability and by the mandatory dispatch/return documents on those movements.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consultant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Primary contact person at the vendor. */
    private String contactPerson;

    private String phone;
    private String email;
    private String address;

    /** e.g. "Computers & peripherals", "Furniture", "HVAC" — free text. */
    private String specialization;

    @Column(name = "active", columnDefinition = "BIT NOT NULL DEFAULT 1")
    @Builder.Default
    private boolean active = true;
}
