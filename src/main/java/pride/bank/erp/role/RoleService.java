package pride.bank.erp.role;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionService;

import java.util.Objects;
import java.util.Optional;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionService permissionService;
    private final RoleSearchDao roleSearchDao;

    public Page<Role> fetchRoles(RoleQueryParams params) {
        RolePage rolePage = RolePage.builder()
                .pageSize(params.getPageSize())
                .sortDirection(determineSortDirection(params.getSortDirection()))
                .pageNumber(params.getPageNumber())
                .sortBy(params.getSortBy())
                .build();
        RoleSearchCriteria roleSearchCriteria = RoleSearchCriteria.builder()
                .name(params.getName())
                .build();
        return roleSearchDao.findAllWithFilters(rolePage, roleSearchCriteria);
    }

    public Role findOneRole(Long roleId) throws NotFoundException {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role with ID " + roleId + " is not found"));
    }

    @Transactional
    public Role createRole(RoleRequestDTO request) {
        ensureNoDuplicateRoleName(request.getName(), null);
        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long roleID, RoleRequestDTO request) throws NotFoundException {
        Role currentData = findOneRole(roleID);
        ensureNoDuplicateRoleName(request.getName(), roleID);
        currentData.setName(request.getName());
        currentData.setDescription(request.getDescription());
        return roleRepository.save(currentData);
    }

    @Transactional
    public Role assignPermissionsToRole(Long roleID, Long permID) throws NotFoundException {
        Role role = findOneRole(roleID);
        Permission permission = permissionService.findOnePermission(permID);
        role.getPermissions().add(permission);
        return roleRepository.save(role);
    }

    @Transactional
    public Role removePermissionsFromRole(Long roleID, Long permID) throws NotFoundException {
        Role role = findOneRole(roleID);
        Permission permission = permissionService.findOnePermission(permID);
        role.getPermissions().remove(permission);
        return roleRepository.save(role);
    }

    @Transactional
    public DeleteResponse deleteRole(Long roleID) throws NotFoundException {
        findOneRole(roleID);
        roleRepository.deleteById(roleID);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private void ensureNoDuplicateRoleName(String name, Long excludeId) {
        Optional<Role> existing = roleRepository.findByNameIgnoreCase(name);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    String.format("A role with the name '%s' already exists", name));
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
