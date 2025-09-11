/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) Boolean isEnabled,
            @RequestParam(required = false) Boolean isAccountNonLocked,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String staffNumber,
            @RequestParam(required = false) String title
    ) {
        Page<User> users = userService.fetchUsers(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                firstName,
                lastName,
                email,
                phone,
                blocked,
                isEnabled,
                isAccountNonLocked,
                name,
                staffNumber,
                title
        );
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserDTO user)
            throws NotFoundException {
        UserResponse response = userService.saveUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<User> updateUser(@PathVariable Long userId, @RequestBody @Valid UserDTO user)
            throws NotFoundException {
        User response = userService.updateUser(user, userId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/users/{userID}")
    public ResponseEntity<User> getSingleUser(
            @PathVariable Long userID
    ) throws NotFoundException {
        User user = userService.findUser(userID);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @PostMapping("/users/{userID}/disable")
    public ResponseEntity<User> deleteUser(@PathVariable Long userID)
            throws NotFoundException {
        User response = userService.deleteUser(userID);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/enable")
    public ResponseEntity<User> enableUser(@PathVariable Long userID)
            throws NotFoundException {
        User response = userService.enableUser(userID);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/unblock")
    public ResponseEntity<User> unblockUser(@PathVariable Long userID)
            throws NotFoundException {
        User response = userService.unblockUser(userID);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/users/search")
    public ResponseEntity<Page<User>> searchUserByEmailNameStaffNumber(
            @RequestParam String text,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<User> users = userService.searchUserByEmailNameStaffNumber(text, pageable);
        return ResponseEntity.ok(users);
    }

    @PutMapping(value = "users/image/{userID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> uploadUserImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @PathVariable Long userID
    ) throws IOException, NotFoundException {
        User response = userService.uploadUserImage(file, userID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("users/remove/image/{userID}")
    public ResponseEntity<User> removeUserImage(
            @PathVariable Long userID
    ) throws NotFoundException {
        User response = userService.removeUserImage(userID);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("users/change-password")
    public ResponseEntity<User> changeUserPassword(
            @RequestBody ChangePasswordDTO request
    ) {
        User response = userService.changePassword(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}