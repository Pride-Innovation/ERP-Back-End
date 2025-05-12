/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.district.District;
import pride.bank.erp.region.Region;
import pride.bank.erp.title.Title;
import pride.bank.erp.user.User;

import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String telephone;
    private String email;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.PERSIST)
    @JsonIgnore
    private Set<User> users;

    @OneToOne
    @JoinColumn(name = "branch_manager_id", unique = true)
    private User branchManager;

    @OneToOne
    @JoinColumn(name = "branch_operations_manager_id", unique = true)
    private User branchOperationsManager;

    @OneToOne
    @JoinColumn(name = "relationship_manager_id", unique = true)
    private User relationshipManager;

    @OneToOne
    @JoinColumn(name = "credit_administrator_id", unique = true)
    private User creditAdministrator;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.PERSIST)
    @JsonIgnore
    private Set<Title> titles;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "region_id")
    private Region region;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "district_id")
    private District district;
}
