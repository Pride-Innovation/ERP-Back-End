/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.role;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.permission.Permission;
import pride.bank.erp.permission.PermissionService;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private RoleSearchDao roleSearchDao;

    public Role createRole(Role role) {
        return roleRepository.save(role);
    }

    public Role findOneRole(Long roleId) throws NotFoundException {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("Role with ID " + roleId + " is not found"));
    }

    public Role assignPermissionsToRole(Long roleID, Long permID) throws NotFoundException {
        Role role = findRoleOrThrow(roleID);
        Permission permission = findPermissionOrThrow(permID);

        if (role.getPermissions().add(permission)) {
            return roleRepository.save(role);
        }
        return role;
    }

    public Role removePermissionsFromRole(Long roleID, Long permID) throws NotFoundException {
        Role role = findRoleOrThrow(roleID);
        Permission permission = findPermissionOrThrow(permID);

        if (role.getPermissions().remove(permission)) {
            return roleRepository.save(role);
        }
        return role;
    }

    private Role findRoleOrThrow(Long roleID) throws NotFoundException {
        return roleRepository.findById(roleID)
                .orElseThrow(() -> new NotFoundException("Role with ID " + roleID + " not found"));
    }

    private Permission findPermissionOrThrow(Long permID) throws NotFoundException {
        return permissionService.findOnePermission(permID);
    }

    public DeleteResponse deleteRole(Long roleID) {
        DeleteResponse response = new DeleteResponse();
        if (roleRepository.existsById(roleID)) {
            roleRepository.deleteById(roleID);
            response.setMessage(Constants.DELETED_SUCCESSFULLY);
            response.setSuccess(true);
        } else {
            response.setMessage(Constants.ENTITY_NOT_FOUND);
            response.setSuccess(false);
        }
        return response;
    }

    public Role updateRole(Long roleID, Role role) throws NotFoundException {
        Role currentData = findOneRole(roleID);
        currentData.setName(role.getName());
        return roleRepository.save(currentData);
    }

    public Page<Role> fetchRoles(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {

        try {
            RolePage rolePage = RolePage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .pageNumber(pageNumber)
                    .sortBy(sortBy)
                    .build();
            RoleSearchCriteria roleSearchCriteria = RoleSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return roleSearchDao.findAllWithFilters(rolePage, roleSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
