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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import pride.bank.erp.apiResponse.Response;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.helper.PasswordGenerator;
import pride.bank.erp.notification.Email;
import pride.bank.erp.title.Title;
import pride.bank.erp.title.TitleService;

import java.util.Objects;

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

    public User findUser(Long userId) throws NotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with ID " + userId + " not found"));
    }

    public Response saveUser(User user, Long titleId)
            throws NotFoundException {
        Title title = titleService.findTitle(titleId);

        String generatedPassword = PasswordGenerator.generatePassword();

        User newUser = User.builder()
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .gender(user.getGender())
                .available(user.isAvailable())
                .staffNumber(user.getStaffNumber())
                .title(title)
                .isEnabled(false)
                .password(passwordEncoder.encode(generatedPassword))
                .build();

        String token = jwtService.generateToken(newUser);
        boolean emailSent = emailService
                .sendEmailVerification(
                        newUser.getEmail(),
                        "Account verification",
                        token,
                        "Verify Your Account",
                        "Verify Account",
                        generatedPassword,
                        "authentication",
                        null
                );

        if (!emailSent) {
            return new Response("failed", "Failed to send verification email to " + newUser.getEmail());
        }

        User savedUser = userRepository.save(newUser);
        return new Response("success", "Verification email has been sent to " + savedUser.getEmail());
    }

    public User updateUser(@NonNull User request, Long titleId)
            throws NotFoundException {
        User user = findUser(request.getId());
        Title title = titleService.findTitle(titleId);

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setStaffNumber(request.getStaffNumber());
        user.setGender(request.getGender());
        user.setAvailable(request.isAvailable());
        user.setTitle(title);
        user.setEmail(request.getEmail());

        return userRepository.save(user);
    }

    public DeleteResponse deleteUser(Long userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    public Page<User> fetchUsers(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String firstName,
            String lastName,
            String email,
            String phone,
            String address
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
                    .firstName(lastName)
                    .address(address)
                    .email(email)
                    .phone(phone)
                    .build();
            return userSearchDao.findAllWithFilters(userPage, userSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

}
