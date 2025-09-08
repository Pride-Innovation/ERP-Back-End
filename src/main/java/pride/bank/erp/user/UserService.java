/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.apiResponse.Response;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.helper.PasswordGenerator;
import pride.bank.erp.notification.Email;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleService;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

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


    public User findUser(Long userId) throws NotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with ID " + userId + " not found"));
    }

    @Transactional
    public UserResponse saveUser(UserDTO user)
            throws NotFoundException {
        Title title = titleService.findTitle(user.getTitle());
        Optional<Branch> branch = branchRepository.findById(user.getBranch());
        assert branch.isPresent();

        Department department = null;

        if (user.getDepartment() != null) {
            department = departmentRepository.findById(user.getDepartment()).orElse(null);
        }

        String generatedPassword = PasswordGenerator.generatePassword();

        User newUser = User.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .gender(user.getGender())
                .availability(user.getAvailability())
                .staffNumber(user.getStaffNumber())
                .title(title)
                .branch(branch.get())
                .department(department)
                .isEnabled(false)
                .blocked(false)
                .isAccountNonLocked(true)
                .password(passwordEncoder.encode(generatedPassword))
                .build();

        String token = jwtService.generateToken(newUser);
        ExtractNumberFromEmailAddress cleanedEmail = new ExtractNumberFromEmailAddress(newUser.getEmail());

        boolean emailSent = emailService
                .sendEmailVerification(
                        cleanedEmail.getCleanedEmail(),
                        "Account verification",
                        token,
                        "Verify Your Account",
                        "Verify Account",
                        generatedPassword,
                        "authentication",
                        null,
                        null
                );

        if (!emailSent) {
            Response emailResponse = new Response("failed",
                    "Failed to send verification email to " + newUser.getEmail());
            return UserResponse
                    .builder()
                    .response(emailResponse)
                    .user(null)
                    .build();
        }

        User savedUser = userRepository.save(newUser);
        Response emailResponse = new Response("success",
                "Verification email has been sent to " + savedUser.getEmail());
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

        Optional<Branch> branch = branchRepository.findById(request.getBranch());
        assert branch.isPresent();

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
        user.setBranch(branch.get());

        return userRepository.save(user);
    }

    public User deleteUser(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setEnabled(false);
        user.setAccountNonLocked(false);
        return userRepository.save(user);
    }

    public User enableUser(Long userId) throws NotFoundException {
        User user = findUser(userId);
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        return userRepository.save(user);
    }

    public User unblockUser(Long userID) throws NotFoundException {
        User user = findUser(userID);
        user.setAccountNonLocked(true);
        return userRepository.save(user);
    }

    public Page<User> fetchUsers(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String firstName,
            String lastName,
            String email,
            String phone
    ) {
        try {
            UserPage userPage = UserPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();
            UserSearchCriteria userSearchCriteria = UserSearchCriteria
                    .builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .phone(phone)
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


}
