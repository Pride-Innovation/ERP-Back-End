/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.tracking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One issued tracking number and the trip it belongs to - see {@link TrackingNumberService}.
 *
 * <p>IDENTITY rather than the AUTO used elsewhere: the id <em>is</em> the sequence the number is
 * built from, and the database's AUTO_INCREMENT hands each concurrent issue its own value with no
 * table of high values to contend over.
 */
@Entity
@Table(name = "tracking_number")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingNumber {

    public enum OwnerType { MOVEMENT, CONSIGNMENT }

    public enum Status {
        ACTIVE,
        /** A movement's own number, set aside when it was loaded onto a consignment. */
        SUPERSEDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null only for the instant between inserting the row and formatting its number. */
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by")
    private Long issuedBy;
}
