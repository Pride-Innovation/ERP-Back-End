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
    public ResponseEntity<Page<UserResponseDTO>> getAllUsers(UserQueryParams params) {
        Page<UserResponseDTO> users = userService.fetchUsers(params).map(UserResponseDTO::from);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserDTO user)
            throws NotFoundException {
        UserResponse response = userService.saveUser(user);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable Long userId, @RequestBody @Valid UserDTO user)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.updateUser(user, userId)), HttpStatus.CREATED);
    }

    /**
     * Your own record.
     *
     * <h2>Why this exists rather than reusing {@code /users/{id}}</h2>
     * The profile page read the by-id endpoint, which is the **staff directory**, so seeing your own
     * profile required {@code READ_USER}. That set off a chain: READ_USER also shows the Users page
     * in the sidebar, the Users page loads roles for one of its filters, and that needs
     * {@code READ_ROLE} — so giving an officer sight of their own profile meant granting them two
     * administrative permissions and a page they have no business on.
     *
     * <p>A permission chain like that is not a policy, it is an accident of which endpoint a page
     * happened to call. This is self-service — it resolves the account from the JWT and takes no id,
     * so it can only ever return the caller's own record — and it answers to authentication alone,
     * exactly as {@code change-password} and the profile picture now do.
     *
     * <p>{@code READ_USER} goes back to meaning what it should: *may you browse the directory*.
     */
    @GetMapping("/users/me")
    public ResponseEntity<UserResponseDTO> getOwnProfile() {
        return new ResponseEntity<>(
                UserResponseDTO.from(userService.currentUser()), HttpStatus.OK);
    }

    /**
     * The one list behind every "choose a person" dropdown in the application.
     *
     * <p>Authentication alone, for the same reason {@code /users/me} is: picking a colleague from a
     * dropdown is not administering the staff directory. Every such dropdown used to load
     * {@code GET /users} and so required {@code READ_USER} — which also opens the Users page and
     * needs {@code READ_ROLE} behind it, so ordinary screens were demanding administrative rights
     * in order to work at all.
     *
     * <p>Scoped exactly as the directory is, so nothing is widened; narrowed in shape, which is what
     * makes opening it to every signed-in user defensible.
     */
    @GetMapping("/users/picker")
    public ResponseEntity<Page<StaffOptionDTO>> staffOptions(
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "false") boolean excludeSelf,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        return new ResponseEntity<>(
                userService.staffOptions(name, excludeSelf, PageRequest.of(pageNumber, pageSize)),
                HttpStatus.OK);
    }

    @GetMapping("/users/{userID}")
    public ResponseEntity<UserResponseDTO> getSingleUser(
            @PathVariable Long userID
    ) throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.findUser(userID)), HttpStatus.OK);
    }

    @PostMapping("/users/{userID}/disable")
    public ResponseEntity<UserResponseDTO> deleteUser(@PathVariable Long userID)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.deleteUser(userID)), HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/enable")
    public ResponseEntity<UserResponseDTO> enableUser(@PathVariable Long userID)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.enableUser(userID)), HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/unblock")
    public ResponseEntity<UserResponseDTO> unblockUser(@PathVariable Long userID)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.unblockUser(userID)), HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/block")
    public ResponseEntity<UserResponseDTO> blockUser(@PathVariable Long userID)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.blockUser(userID)), HttpStatusCode.valueOf(201));
    }

    @PostMapping("/users/{userID}/return-from-leave")
    public ResponseEntity<UserResponseDTO> returnUserFromLeave(@PathVariable Long userID)
            throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.returnUserFromLeave(userID)), HttpStatusCode.valueOf(201));
    }

    @GetMapping("/users/search")
    public ResponseEntity<Page<UserResponseDTO>> searchUserByEmailNameStaffNumber(
            @RequestParam String text,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<UserResponseDTO> users = userService.searchUserByEmailNameStaffNumber(text, pageable)
                .map(UserResponseDTO::from);
        return ResponseEntity.ok(users);
    }

    @PutMapping(value = "users/image/{userID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDTO> uploadUserImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @PathVariable Long userID
    ) throws IOException, NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.uploadUserImage(file, userID)), HttpStatus.CREATED);
    }

    @PutMapping("users/remove/image/{userID}")
    public ResponseEntity<UserResponseDTO> removeUserImage(
            @PathVariable Long userID
    ) throws NotFoundException {
        return new ResponseEntity<>(UserResponseDTO.from(userService.removeUserImage(userID)), HttpStatus.CREATED);
    }

    @PostMapping("users/change-password")
    public ResponseEntity<UserResponseDTO> changeUserPassword(
            @RequestBody ChangePasswordDTO request
    ) {
        return new ResponseEntity<>(UserResponseDTO.from(userService.changePassword(request)), HttpStatus.CREATED);
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
