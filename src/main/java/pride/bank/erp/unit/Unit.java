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
}
