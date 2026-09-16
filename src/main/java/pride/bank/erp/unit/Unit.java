/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.department.Department;
import pride.bank.erp.role.Role;

import java.util.HashSet;
import java.util.Set;

/**
 * A unit sits under a department (Department 1 — N Units) and owns a group mailbox
 * (e.g. infrastructure@pridebank.co.ug for the Infrastructure unit under Business Technology).
 * Users may optionally belong to a unit (Unit 1 — N Users, via {@code User.unit}). Workflow
 * group-email steps that target a unit's mailbox are actionable by that unit's members.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Unit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** The unit's shared mailbox, e.g. infrastructure@pridebank.co.ug. */
    private String groupEmail;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * Roles conferred on every member of this unit.
     *
     * <p>This is what gives a unit standing. Before it, a unit was a group mailbox with a department
     * and nothing else: it was consulted only for {@code GROUP_EMAIL} workflow routing and the
     * resulting inbox, so an Admin Unit officer and a branch officer holding the same title role had
     * identical rights over every action in the application. The seniority the bank actually works
     * to was modeled on role names — {@code ADMIN_OFFICER}, {@code ADMIN_MANAGER} — that were never
     * seeded, so the code expressing it never ran.
     *
     * <p>Membership now grants: adding someone to Admin Unit confers the unit's roles, removing them
     * revokes them, and there is one place to configure it. The roles here are ordinary {@link Role}
     * rows, so they are granted permissions on the same Settings screen as any other role.
     *
     * <p>EAGER because {@code User.unit} is EAGER and {@link pride.bank.erp.user.User#getAuthorities()}
     * walks straight through to the permissions — a lazy collection would fail outside a session,
     * which is exactly where authorities are read.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "unit_role",
            joinColumns = @JoinColumn(name = "unit_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
