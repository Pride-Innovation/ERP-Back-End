/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.permission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;

    @PostMapping("/permissions")
    public ResponseEntity<?> createPermission(@RequestBody Permission permission) {
        try {
            Permission response = permissionService.createPermission(permission);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<Permission>> fetchAllPermissions() {
        try {
            List<Permission> permissions = permissionService.fetchAllPermissions();
            return new ResponseEntity<>(permissions, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @DeleteMapping("/permissions/{permID}")
    public ResponseEntity<?> deletePermission(@PathVariable Long permID) {
        try {
            var response = permissionService.deletePermission(permID);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/permissions/{permID}")
    public ResponseEntity<?> updatePermission(@PathVariable Long permID, @RequestBody Permission request) {
        try {
            Permission response = permissionService.updatePermission(permID, request);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
