/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.apiResponse.Response;
import pride.bank.erp.availability.Leave;
import pride.bank.erp.availability.LeaveRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.helper.PasswordGenerator;
import pride.bank.erp.notification.Email;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleRepository;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.title.TitleService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class UserService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final Email emailService;
    private final UserSearchDao userSearchDao;
    private final TitleService titleService;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final FileUpload fileUpload;
    private final CurrentUserService currentUserService;
    private final LeaveRepository leaveRepository;
    private final TitleRepository titleRepository;
    private final RoleRepository roleRepository;

    public User findUser(Long userId) throws NotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with ID " + userId + " not found"));
    }

    @Transactional
    public UserResponse saveUser(UserDTO user)
            throws NotFoundException {

        // Duplicate checks with descriptive errors
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new BadRequestException("An account with the email address '" + user.getEmail() + "' already exists.");
        }
        if (userRepository.existsByStaffNumber(user.getStaffNumber())) {
            throw new BadRequestException("An account with staff number '" + user.getStaffNumber() + "' already exists.");
        }

        Title title = titleService.findTitle(user.getTitle());
        Branch branch = branchRepository.findById(user.getBranch())
                .orElseThrow(() -> new NotFoundException("Duty Station / Branch with ID " + user.getBranch() + " not found."));

        Department department = null;
        if (user.getDepartment() != null) {
            department = departmentRepository.findById(user.getDepartment()).orElse(null);
        }

        // Default availability to 'present' when not supplied
        String availability = (user.getAvailability() != null && !user.getAvailability().isBlank())
                ? user.getAvailability()
                : "present";

        String generatedPassword = PasswordGenerator.generatePassword();

        User newUser = User.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .gender(user.getGender())
                .availability(availability)
                .staffNumber(user.getStaffNumber())
                .title(title)
                .branch(branch)
                .department(department)
                .isEnabled(false)
                .blocked(false)
                .isAccountNonLocked(true)
                .password(passwordEncoder.encode(generatedPassword))
                .build();

        String token = jwtService.generateToken(newUser);
        String recipientFullName = user.getFirstName() + " " + user.getLastName();

        boolean emailSent = emailService
                .sendEmailVerification(
                        newUser.getEmail(),
                        "Welcome to Pride Bank — Verify Your Account",
                        token,
                        "Account Verification",
                        "Verify My Account",
                        generatedPassword,
                        "authentication",
                        null,
                        null,
                        null,
                        null,
                        recipientFullName          // passed via the reason parameter
                );

        if (!emailSent) {
            Response emailResponse = new Response("failed",
                    "Failed to send verification email to " + newUser.getEmail() + ". Please contact the system administrator.");
            return UserResponse.builder()
                    .response(emailResponse)
                    .user(null)
                    .build();
        }

        User savedUser = userRepository.save(newUser);
        Response emailResponse = new Response("success",
                "Account created successfully. A verification email has been sent to " + savedUser.getEmail() + ".");
        return UserResponse
                .builder()
                .response(emailResponse)
                .user(UserResponseDTO.from(savedUser))
                .build();
    }


    @Transactional
    public User updateUser(@NonNull UserDTO request, Long userId)
            throws NotFoundException {
        User user = findUser(userId);
        Title title = titleService.findTitle(request.getTitle());

        // Duplicate checks excluding the current user's own values
        if (userRepository.existsByEmailAndIdNot(request.getEmail(), userId)) {
            throw new BadRequestException("An account with the email address '" + request.getEmail() + "' already exists.");
        }
        if (userRepository.existsByStaffNumberAndIdNot(request.getStaffNumber(), userId)) {
            throw new BadRequestException("An account with staff number '" + request.getStaffNumber() + "' already exists.");
        }

        Branch branch = branchRepository.findById(request.getBranch())
                .orElseThrow(() -> new NotFoundException("Duty Station / Branch with ID " + request.getBranch() + " not found."));

        Department department = null;
        if (request.getDepartment() != null) {
            department = departmentRepository.findById(request.getDepartment()).orElse(null);
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setOtherName(request.getOtherName());
        user.setStaffNumber(request.getStaffNumber());
        user.setGender(request.getGender());
        user.setAvailability(request.getAvailability());
        user.setTitle(title);
        user.setDepartment(department);
        user.setEnabled(request.isEnabled());
        user.setAccountNonLocked(request.isAccountNonLocked());
        user.setEmail(request.getEmail());
        user.setBlocked(request.isBlocked());
        user.setBranch(branch);

        return userRepository.save(user);
    }

    public User deleteUser(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setEnabled(false);
        return userRepository.save(user);
    }

    public User blockUser(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setBlocked(true);
        user.setAccountNonLocked(false);
        return userRepository.save(user);
    }

    public User enableUser(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        user.setBlocked(false);
        return userRepository.save(user);
    }

    @Transactional
    public User unblockUser(Long userID) throws NotFoundException {
        User user = findUser(userID);
        user.setAccountNonLocked(true);
        user.setBlocked(false);
        user.setFailedLoginAttempts(0);
        return userRepository.save(user);
    }

    @Transactional
    public User returnUserFromLeave(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setEnabled(true);
        checkIfUserIsReturningFromLeave(user);
        return userRepository.save(user);
    }

    @Transactional
    protected void checkIfUserIsReturningFromLeave(User user) {
        LocalDateTime now = LocalDateTime.now();

        // For scheduled returns: find a leave that has ended; for manual returns: find any active leave
        Optional<Leave> leaveOpt = leaveRepository.findActiveLeaveByUser(user.getId());
        if (leaveOpt.isEmpty()) {
            leaveOpt = leaveRepository.findActiveLeaveEndedByUser(user.getId(), now);
        }

        if (leaveOpt.isPresent()) {
            Leave leave = leaveOpt.get();
            User actingUser = leave.getActingUser();

            if (actingUser != null) {
                actingUser.setActing(false);
                actingUser.setActingTitle(null);
                userRepository.save(actingUser);
            }

            leave.setActive(false);
            leaveRepository.save(leave);
        }
    }

    public Page<User> fetchUsers(
            UserQueryParams params
    ) {
        try {
            UserPage userPage = UserPage.builder()
                    .pageSize(params.getPageSize())
                    .sortDirection(determineSortDirection(params.getSortDirection()))
                    .sortBy(params.getSortBy())
                    .pageNumber(params.getPageNumber())
                    .build();
            UserSearchCriteria userSearchCriteria = UserSearchCriteria
                    .builder()
                    .firstName(params.getFirstName())
                    .lastName(params.getLastName())
                    .email(params.getEmail())
                    .phone(params.getPhone())
                    .blocked(params.getBlocked())
                    .isAccountNonLocked(params.getIsAccountNonLocked())
                    .isEnabled(params.getIsEnabled())
                    .name(params.getName())
                    .staffNumber(params.getStaffNumber())
                    .title(params.getTitle())
                    .gender(params.getGender())
                    .titleId(params.getTitleId())
                    .branchId(params.getBranchId())
                    .departmentId(params.getDepartmentId())
                    .roleId(params.getRoleId())
                    .createdAtFrom(params.getCreatedAtFrom())
                    .createdAtTo(params.getCreatedAtTo())
                    .build();
            return userSearchDao.findAllWithFilters(userPage, userSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    public Page<User> searchUserByEmailNameStaffNumber(String text, Pageable pageable) {
        return userRepository.searchByNameEmailOrStaffNumber(text, pageable);
    }

    public User uploadUserImage(MultipartFile file, Long userID) throws NotFoundException, IOException {
        User user = findUser(userID);
        String filePath = null;

        if (file != null && !file.isEmpty()) {
            try {
                filePath = fileUpload.uploadFileToLocalDirectory(file);
            } catch (IOException ex) {
                throw new IOException("Failed to upload file: " + ex.getMessage(), ex);
            }
        }
        user.setProfileImage(filePath);
        return userRepository.save(user);
    }

    public User removeUserImage(Long userID) throws NotFoundException {
        User user = findUser(userID);
        user.setProfileImage(null);
        return userRepository.save(user);
    }

    public User changePassword(ChangePasswordDTO request) {
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();

        User user = currentUserService.getCurrentUser();

        // Validate that the old password matches the stored password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("Old password is incorrect.");
        }

        // Optionally: prevent user from reusing the same password
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the old password.");
        }

        // Set new password
        String encodedNewPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedNewPassword);

        return userRepository.save(user);
    }

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Map<String, Object> bulkInsertUsers(List<BulkUserDTO> userList) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int successCount = 0;
        int total = userList == null ? 0 : userList.size();

        for (int i = 0; i < total; i++) {
            BulkUserDTO dto = userList.get(i);
            // Prefer the spreadsheet's own "No." column; fall back to array index + 1.
            long rowNumber = dto.getNo() != null ? dto.getNo() : (i + 1);

            try {
                insertSingleUserRow(dto);
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("row", rowNumber);
                err.put("name", buildFullName(dto));
                err.put("staffNumber", dto.getStaffNumber());
                err.put("email", dto.getEmail());
                err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errors.add(err);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // "success" reflects whether *any* row was inserted, not whether every row was perfect —
        // partial success is still a successful import per the bulk-insert contract.
        result.put("success", successCount > 0);
        result.put("total", total);
        result.put("inserted", successCount);
        result.put("failed", errors.size());
        result.put("errors", errors);

        return result;
    }

    // Each row commits independently because bulkInsertUsers is not @Transactional
    // and Spring Data's save() opens its own implicit transaction per call. A row
    // failure throws out of here and is caught by the loop without affecting earlier rows.
    private void insertSingleUserRow(BulkUserDTO dto) {
        // Required field presence
        if (isBlank(dto.getFirstName())) throw new BadRequestException("First Name is required.");
        if (isBlank(dto.getLastName())) throw new BadRequestException("Last Name is required.");
        if (isBlank(dto.getEmail())) throw new BadRequestException("Email is required.");
        if (isBlank(dto.getStaffNumber())) throw new BadRequestException("Staff Number is required.");
        if (isBlank(dto.getGender())) throw new BadRequestException("Gender is required.");
        if (isBlank(dto.getTitle())) throw new BadRequestException("Title is required.");
        if (isBlank(dto.getDutyStation())) throw new BadRequestException("Duty Station / Branch is required.");

        // Email format
        if (!EMAIL_PATTERN.matcher(dto.getEmail().trim()).matches()) {
            throw new BadRequestException("Email '" + dto.getEmail() + "' is not a valid email address.");
        }

        // Uniqueness (within the existing DB)
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BadRequestException("Email '" + dto.getEmail() + "' already exists.");
        }
        if (userRepository.existsByStaffNumber(dto.getStaffNumber())) {
            throw new BadRequestException("Staff Number '" + dto.getStaffNumber() + "' already exists.");
        }

        // Strict lookups — no silent fallbacks
        Title title = titleRepository.findByNameIgnoreCase(dto.getTitle())
                .orElseThrow(() -> new BadRequestException("Title '" + dto.getTitle() + "' not found."));

        Branch branch = branchRepository.findByNameIgnoreCase(dto.getDutyStation())
                .orElseThrow(() -> new BadRequestException("Duty Station / Branch '" + dto.getDutyStation() + "' not found."));

        Department department = null;
        if (!isBlank(dto.getDepartment())) {
            department = departmentRepository.findByNameIgnoreCase(dto.getDepartment())
                    .orElseThrow(() -> new BadRequestException("Department '" + dto.getDepartment() + "' not found."));
        }

        Set<Role> additionalRoles = new HashSet<>();
        if (!isBlank(dto.getRole())) {
            Role role = roleRepository.findByNameIgnoreCase(dto.getRole())
                    .orElseThrow(() -> new BadRequestException("Role '" + dto.getRole() + "' not found."));
            additionalRoles.add(role);
        }

        String firstName = dto.getFirstName().trim();
        String lastName = dto.getLastName().trim();
        String otherName = isBlank(dto.getOtherName()) ? null : dto.getOtherName().trim();

        String generatedPassword = PasswordGenerator.generatePassword();

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .otherName(otherName)
                .staffNumber(dto.getStaffNumber())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(generatedPassword))
                .gender(dto.getGender())
                .availability("present")
                .title(title)
                .branch(branch)
                .department(department)
                .additionalRoles(additionalRoles)
                .isEnabled(false)
                .blocked(false)
                .isAccountNonLocked(true)
                .build();

        userRepository.save(user);

        String recipientFullName = firstName + " " + lastName;
        emailService.sendEmailVerification(
                dto.getEmail(),
                "Welcome to Pride Bank — Verify Your Account",
                null,
                "Account Verification",
                "Verify My Account",
                generatedPassword,
                "authentication",
                null, null, null, null, recipientFullName
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String buildFullName(BulkUserDTO dto) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(dto.getFirstName())) sb.append(dto.getFirstName().trim());
        if (!isBlank(dto.getOtherName())) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(dto.getOtherName().trim());
        }
        if (!isBlank(dto.getLastName())) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(dto.getLastName().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

}
