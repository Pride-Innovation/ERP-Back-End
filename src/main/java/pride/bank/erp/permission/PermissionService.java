package pride.bank.erp.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.role.Role;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public List<Permission> fetchAllPermissions() {
        return permissionRepository.findAll();
    }

    public Permission findOnePermission(Long permID) throws NotFoundException {
        return permissionRepository.findById(permID)
                .orElseThrow(() -> new NotFoundException("Permission with ID: " + permID + " not found"));
    }

    @Transactional
    public Permission createPermission(PermissionRequestDTO request) {
        ensureNoDuplicatePermissionName(request.getName(), null);
        Permission permission = Permission.builder()
                .name(request.getName())
                .build();
        return permissionRepository.save(permission);
    }

    @Transactional
    public Permission updatePermission(Long permID, PermissionRequestDTO request) throws NotFoundException {
        Permission permission = findOnePermission(permID);
        ensureNoDuplicatePermissionName(request.getName(), permID);
        permission.setName(request.getName());
        return permissionRepository.save(permission);
    }

    @Transactional
    public DeleteResponse deletePermission(Long permID) throws NotFoundException {
        Permission permission = findOnePermission(permID);
        for (Role role : permission.getRoles()) {
            role.getPermissions().remove(permission);
        }
        permissionRepository.delete(permission);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private void ensureNoDuplicatePermissionName(String name, Long excludeId) {
        Optional<Permission> existing = permissionRepository.findByNameIgnoreCase(name);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    String.format("A permission with the name '%s' already exists", name));
        }
    }
}
