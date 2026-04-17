/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<Page<User>> getAllUsers(UserQueryParams params) {
        Page<User> users = userService.fetchUsers(params);
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

    @PostMapping("/users/{userID}/block")
    public ResponseEntity<User> blockUser(@PathVariable Long userID)
            throws NotFoundException {
        User response = userService.blockUser(userID);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/return-from-leave")
    public ResponseEntity<User> returnUserFromLeave(@PathVariable Long userID)
            throws NotFoundException {
        User response = userService.returnUserFromLeave(userID);
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

    @PostMapping("users/bulk-insert")
    public ResponseEntity<Map<String, Object>> bulkInsertUsers(
            @RequestParam("users") String users
    ) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        List<BulkUserDTO> userList = objectMapper.readValue(users,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, BulkUserDTO.class));

        Map<String, Object> response = userService.bulkInsertUsers(userList);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}