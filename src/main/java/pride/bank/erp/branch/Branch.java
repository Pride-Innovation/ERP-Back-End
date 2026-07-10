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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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
    private String shortCode;

    /**
     * True when this branch represents the head office. Used by workflow
     * resolution to choose between {@code HEAD_OFFICE} and {@code BRANCH} scope.
     * Defaults to false; the head-office row is flagged via the startup seeder.
     *
     * <p>Column definition includes a DEFAULT so auto-DDL can add this NOT NULL
     * column to existing branch tables without rejecting pre-existing rows.
     */
    @Column(name = "is_head_office", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean isHeadOffice = false;

    /**
     * Distribution-list email that reaches every manager of this branch. Used as the
     * dynamic CC target when a workflow step's {@code notifyGroupSource} is
     * {@code REQUESTER_MANAGERS_GROUP} and the requester is at a branch (not HO).
     */
    private String managersGroupEmail;

    // The User/Title back-references below are excluded from Lombok toString/equals:
    // @Data includes even @JsonIgnore'd fields, so any toString() (e.g. Spring's debug
    // "Writing [...]" log) recursed Branch → manager User → title → branch… into a
    // StackOverflowError.
    @OneToMany(mappedBy = "branch", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<User> users;

    /*
    @OneToOne
    @JoinColumn(name = "branch_manager_id", unique = true)
    This is to ensure a unique entry on the column
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_manager_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User branchManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_operations_manager_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User branchOperationsManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relationship_manager_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User relationshipManager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_administrator_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User creditAdministrator;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Title> titles;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "region_id")
    private Region region;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "district_id")
    private District district;
}
