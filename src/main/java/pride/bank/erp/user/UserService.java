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
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.helper.PasswordGenerator;
import pride.bank.erp.notification.Email;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleRepository;
import pride.bank.erp.title.TitleService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

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
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(newUser.getEmail());
        String recipientFullName = user.getFirstName() + " " + user.getLastName();

        boolean emailSent = emailService
                .sendEmailVerification(
                        cleanedEmail.getCleanedEmail(),
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
            return UserResponse
                    .builder()
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
                .user(savedUser)
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

    @Transactional
    public Map<String, Object> bulkInsertUsers(List<BulkUserDTO> userList) {
        Branch headOffice = branchRepository.findById(1L).orElse(null);
        Map<String, Object> result = new HashMap<>();

        List<String> errors = new ArrayList<>();
        int successCount = 0;

        // TO DO -> :::: Ensure that email and stuff number is a unique field.

        for (BulkUserDTO dto : userList) {
            try {
                // Split name
                String[] nameParts = dto.getName().trim().split("\\s+");
                String firstName = nameParts.length > 0 ? nameParts[0] : null;
                String lastName = nameParts.length > 1 ? nameParts[nameParts.length - 1] : null;
                String otherName = nameParts.length > 2
                        ? String.join(" ", Arrays.copyOfRange(nameParts, 1, nameParts.length - 1))
                        : null;

                // Title / Branch / Department (null if not found)
                Title title = titleRepository.findByNameIgnoreCase(dto.getTitle()).orElse(null);
                Branch branch = branchRepository.findByNameIgnoreCase(dto.getDutyStation()).orElse(null);
                Department department = null;
                if (branch == null) {
                    department = departmentRepository.findByNameIgnoreCase(dto.getDutyStation()).orElse(null);
                    branch = headOffice;
                }

                // Generate password
                String generatedPassword = PasswordGenerator.generatePassword();

                // Create and save user
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
                        .isEnabled(false)
                        .blocked(false)
                        .isAccountNonLocked(true)
                        .build();

                userRepository.save(user);

                // Notify user after successful save
                String recipientFullName = firstName + (lastName != null ? " " + lastName : "");
                ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(dto.getEmail());
                emailService.sendEmailVerification(
                        cleanedEmail.getCleanedEmail(),
                        "Welcome to Pride Bank — Verify Your Account",
                        null,
                        "Account Verification",
                        "Verify My Account",
                        generatedPassword,
                        "authentication",
                        null, null, null, null, recipientFullName
                );

                successCount++;

            } catch (Exception e) {
                errors.add("Failed for staffNumber=" + dto.getStaffNumber() + ": " + e.getMessage());
            }
        }

        result.put("success", errors.isEmpty());
        result.put("inserted", successCount);
        result.put("failed", errors.size());
        result.put("errors", errors);

        return result;
    }

}
