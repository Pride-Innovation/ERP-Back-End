/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.role;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;
import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
public class RoleController {
    @Autowired
    private RoleService roleService;

    @GetMapping("/roles")
    public ResponseEntity<Page<Role>> getRoleWithFilters(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {
        return new ResponseEntity<>(roleService.fetchRoles(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name), HttpStatus.OK);
    }

    @GetMapping("/roles/{roleID}")
    public ResponseEntity<Role> fetchRole(@PathVariable Long roleID)
            throws NotFoundException {
        Role role = roleService.findOneRole(roleID);
        return new ResponseEntity<>(role, HttpStatus.OK);
    }

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(@RequestBody @Valid Role role) {
        Role response = roleService.createRole(role);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/roles/{roleID}/add-permissions/{permID}")
    public ResponseEntity<Role> assignPermissionsToRole(@PathVariable Long roleID, @PathVariable Long permID)
            throws NotFoundException {
        Role response = roleService.assignPermissionsToRole(roleID, permID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/roles/{roleID}/remove-permissions/{permID}")
    public ResponseEntity<Role> removePermissionsToRole(@PathVariable Long roleID, @PathVariable Long permID)
            throws NotFoundException {
        Role response = roleService.removePermissionsFromRole(roleID, permID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/roles/{roleID}")
    public ResponseEntity<Role> updateRole(@PathVariable Long roleID, @RequestBody Role role)
            throws NotFoundException {
        Role response = roleService.updateRole(roleID, role);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/roles/{roleID}")
    public ResponseEntity<DeleteResponse> deleteRole(@PathVariable Long roleID) {
        var response = roleService.deleteRole(roleID);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(204));
    }

}
