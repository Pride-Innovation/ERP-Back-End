/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.district;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;

import java.util.Set;


@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class District {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    /*
     * Out of equals/hashCode/toString, like Region.branches and Title.users.
     *
     * Branch hashes its district, and @Data would hash this collection back - so hashing any branch
     * in a district loaded the whole set, and outside a session threw LazyInitializationException.
     * It lay dormant while no branch had a district; the first one assigned made saving a stocking
     * rule naming that branch fail (StockingRuleService builds a Set<Branch>). Already @JsonIgnore,
     * so excluding it cannot expose a JSON loop.
     */
    @OneToMany(mappedBy = "district", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Set<Branch> branches;
}
