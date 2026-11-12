/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.department.Department;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;

import java.util.Set;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Title {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String shortCode;

    /**
     * Out of equals/hashCode/toString, and that is a fix rather than tidiness.
     *
     * <p>{@code User}'s Lombok hashCode includes its title, and this one included the title's users:
     * a cycle through a lazy collection. Hashing a User initialised this set; loading the set hashes
     * each User into it, which re-enters the set mid-load. Hibernate guards that with an
     * {@code assert}, so with assertions on - every test run - it fails with a bare
     * {@code AssertionError} in {@code PersistentSet.initializeEmptyCollection}, and in production,
     * where they are off, it silently loads the collection a second time. {@code Region#branches}
     * had the same cycle through {@code Branch}. Met by {@code LoanerFlowTest}.
     */
    @OneToMany(mappedBy = "title", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Set<User> users;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reports_to_id")
    private Title reportsTo;

    /** Where the title is used. For filtering only; null is "not set". See {@link TitleWorkplace}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TitleWorkplace workplace;

    /**
     * The department the title belongs to, optional. For filtering only - a user's department is
     * still the user's own field, and nothing derives one from the other.
     *
     * <p>Serialised without the department's branch: a title travels inside every user response,
     * and the name is all a filter or a card needs.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    @JsonIgnoreProperties({"branch", "managersGroupEmail", "hibernateLazyInitializer", "handler"})
    private Department department;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    // @OnDelete(action = OnDeleteAction.CASCADE)
    private Role role;
}
