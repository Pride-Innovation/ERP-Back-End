/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitRepository unitRepository;
    private final DepartmentService departmentService;
    private final RoleRepository roleRepository;
    private final pride.bank.erp.auditing.trail.AuditService auditService;

    public Page<UnitResponse> fetchAllUnits(UnitQueryParams params) {
        Sort sort = Sort.by(
                Constants.ASCENDING_SORT_DIRECTION.equalsIgnoreCase(params.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                params.getSortBy()
        );
        var pageable = PageRequest.of(params.getPageNumber(), params.getPageSize(), sort);

        boolean hasName = params.getName() != null && !params.getName().isBlank();
        boolean hasDepartment = params.getDepartmentId() != null;

        Page<Unit> page;
        if (hasName && hasDepartment) {
            page = unitRepository.findAllByNameContainingIgnoreCaseAndDepartment_Id(
                    params.getName(), params.getDepartmentId(), pageable);
        } else if (hasDepartment) {
            page = unitRepository.findAllByDepartment_Id(params.getDepartmentId(), pageable);
        } else if (hasName) {
            page = unitRepository.findAllByNameContainingIgnoreCase(params.getName(), pageable);
        } else {
            page = unitRepository.findAll(pageable);
        }
        return page.map(UnitResponse::from);
    }

    public Unit findOneUnit(Long id) throws NotFoundException {
        return unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Unit with Id %s is not found", id)));
    }

    public UnitResponse findOneUnitResponse(Long id) throws NotFoundException {
        return UnitResponse.from(findOneUnit(id));
    }

    @Transactional
    public UnitResponse createUnit(UnitDTO request) throws NotFoundException {
        Department department = departmentService.findOneDepartment(request.getDepartmentId());
        ensureNoDuplicateUnitName(request.getName(), request.getDepartmentId(), null);
        Unit unit = Unit.builder()
                .name(request.getName())
                .groupEmail(request.getGroupEmail())
                .department(department)
                .build();
        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse updateUnit(UnitDTO request, Long id) throws NotFoundException {
        Unit unit = findOneUnit(id);
        Department department = departmentService.findOneDepartment(request.getDepartmentId());
        ensureNoDuplicateUnitName(request.getName(), request.getDepartmentId(), id);
        unit.setName(request.getName());
        unit.setGroupEmail(request.getGroupEmail());
        unit.setDepartment(department);
        return UnitResponse.from(unitRepository.save(unit));
    }

    /**
     * Replaces the set of roles this unit confers.
     *
     * <p>The single most consequential edit in Settings: everyone in the unit inherits every
     * permission behind every role named here, immediately, without anything being done to their own
     * account. That is the point — it is how an Admin Unit officer comes to outrank a branch officer
     * holding the same title — and it is also why the call is audited and why the frontend spells the
     * consequences out before sending it.
     *
     * <p>A replacement, not a merge. Sending a list without a role revokes it; sending an empty list
     * revokes them all and leaves the unit as a group mailbox with a department. Everything here has
     * to be revokable, and making revocation the same operation as granting is the only way to be
     * sure it is: there is no second call anyone can forget.
     */
    @Transactional
    public UnitResponse replaceUnitRoles(Long id, List<Long> roleIds) throws NotFoundException {
        Unit unit = findOneUnit(id);

        List<Long> wanted = roleIds == null ? List.of() : roleIds;
        Set<Role> resolved = new LinkedHashSet<>();
        for (Long roleId : wanted) {
            if (roleId == null) continue;
            resolved.add(roleRepository.findById(roleId).orElseThrow(
                    () -> new NotFoundException(String.format("Role with id %s is not found", roleId))));
        }

        Set<String> before = roleNames(unit.getRoles());
        unit.setRoles(new HashSet<>(resolved));
        Unit saved = unitRepository.save(unit);
        Set<String> after = roleNames(saved.getRoles());

        // Recorded even when nothing moved. "Someone opened this and pressed save" is itself worth
        // knowing about a screen that can hand a branch officer the whole bank.
        auditService.record(
                pride.bank.erp.auditing.trail.AuditEventType.UPDATED,
                pride.bank.erp.auditing.trail.AuditModule.SYSTEM,
                "UNIT_ROLES_CHANGED",
                describeChange(saved, before, after),
                before.equals(after)
                        ? pride.bank.erp.auditing.trail.AuditSeverity.INFO
                        : pride.bank.erp.auditing.trail.AuditSeverity.WARNING,
                "Unit", saved.getId());

        return UnitResponse.from(saved);
    }

    private static Set<String> roleNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    /**
     * A one-line account of what changed, written so the audit trail can be read without also
     * having the roles table open.
     */
    private static String describeChange(Unit unit, Set<String> before, Set<String> after) {
        List<String> granted = after.stream().filter(r -> !before.contains(r)).sorted().toList();
        List<String> revoked = before.stream().filter(r -> !after.contains(r)).sorted().toList();

        if (granted.isEmpty() && revoked.isEmpty()) {
            return "Saved roles for unit " + unit.getName() + " with no change ("
                    + (after.isEmpty() ? "none" : String.join(", ", after)) + ")";
        }

        StringBuilder message = new StringBuilder("Unit ").append(unit.getName()).append(": ");
        if (!granted.isEmpty()) {
            message.append("granted ").append(String.join(", ", granted));
        }
        if (!revoked.isEmpty()) {
            if (!granted.isEmpty()) message.append("; ");
            message.append("revoked ").append(String.join(", ", revoked));
        }
        message.append(". Everyone in this unit is affected immediately.");
        return message.toString();
    }

    @Transactional
    public DeleteResponse deleteUnit(Long id) throws NotFoundException {
        findOneUnit(id);
        unitRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    /** Unit names must be unique within a department (the same name may exist under another department). */
    private void ensureNoDuplicateUnitName(String name, Long departmentId, Long excludeId) {
        Optional<Unit> existing = unitRepository.findByNameIgnoreCaseAndDepartment_Id(name, departmentId);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    String.format("A unit named '%s' already exists in this department", name));
        }
    }
}
