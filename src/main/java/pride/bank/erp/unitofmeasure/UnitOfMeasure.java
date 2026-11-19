/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unitofmeasure;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One unit a commodity or an asset may be counted in ("pieces", "boxes", "litres"), managed in
 * Settings → Units of Measure. See V34.
 *
 * <p>The unit is still stored as text on {@code commodity.group_name} and {@code asset.unit_of_measure};
 * this list decides which texts may be chosen. That is why there is no delete and no rename: a unit
 * in use stays readable on the records that carry it, and switching one off only stops it being
 * offered for new choices.
 */
@Entity
@Table(name = "unit_of_measure")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitOfMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored lower-case and trimmed (see {@link UnitOfMeasureService#normalise}). */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
