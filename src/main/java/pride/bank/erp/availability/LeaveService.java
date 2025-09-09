package pride.bank.erp.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.ExtractNumberFromEmailAddress;
import pride.bank.erp.notification.Email;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRepository leaveRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final Email emailService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Retrieve all leave records.
     */
    public List<Leave> findAllLeave() {
        return leaveRepository.findAll();
    }

    /**
     * Creates a leave entry and assigns acting responsibility.
     *
     * @param request leave details
     * @param userId  ID of the user going on leave
     * @return saved leave entity
     * @throws NotFoundException if user(s) are not found
     */
    @Transactional
    public Leave createLeave(LeaveDTO request, Long userId) throws NotFoundException {
        validateLeaveRequest(request, userId);

        User userGoingOnLeave = userService.findUser(userId);
        if (userGoingOnLeave == null) {
            throw new NotFoundException("User going on leave not found with ID: " + userId);
        }

        User actingUser = assignActingUserIfPresent(request, userGoingOnLeave);

        // Deactivate the user going on leave
        deactivateUser(userGoingOnLeave);

        // Save leave record
        Leave leave = leaveRepository.save(buildLeaveEntity(request, userGoingOnLeave, actingUser));

        // Notify acting user
        if (actingUser != null) {
            notifyActingUser(actingUser, userGoingOnLeave, request);
        }

        return leave;
    }

    /**
     * Validates the leave request inputs.
     */
    private void validateLeaveRequest(LeaveDTO request, Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("Leave request cannot be null.");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User going on leave ID must be provided.");
        }
    }

    /**
     * Assigns acting responsibilities if an acting user is provided.
     */
    private User assignActingUserIfPresent(LeaveDTO request, User userGoingOnLeave) throws NotFoundException {
        if (request.getActingUser() == null) return null;

        User actingUser = userService.findUser(request.getActingUser());
        if (actingUser == null) {
            throw new NotFoundException("Acting user not found with ID: " + request.getActingUser());
        }

        actingUser.setActingTitle(userGoingOnLeave.getTitle());
        actingUser.setActing(true);
        userRepository.save(actingUser);

        return actingUser;
    }

    /**
     * Disables a user account who is going on leave.
     */
    private void deactivateUser(User user) {
        user.setAccountNonLocked(false);
        user.setBlocked(true);
        userRepository.save(user);
    }

    /**
     * Builds the leave entity to be saved.
     */
    private Leave buildLeaveEntity(LeaveDTO request, User onLeaveUser, User actingUser) {
        return Leave.builder()
                .leaveType(request.getLeaveType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .active(true)
                .onLeaveUser(onLeaveUser)
                .actingUser(actingUser)
                .build();
    }

    /**
     * Sends a notification email to the acting user.
     */
    private void notifyActingUser(User actingUser, User leavingUser, LeaveDTO request) {
        String toEmail = new ExtractNumberFromEmailAddress(actingUser.getEmail()).getCleanedEmail();
        String formattedStartDate = formatDate(request.getStartDate());
        String formattedEndDate = formatDate(request.getEndDate());

        emailService.sendEmailVerification(
                toEmail,
                "Acting Role",
                actingUser.getFirstName(),
                leavingUser.getFirstName(),
                request.getLeaveType(),
                leavingUser.getTitle().getName(),
                "leave",
                null,
                null,
                formattedStartDate,
                formattedEndDate,
                request.getReason()
        );
    }

    /**
     * Formats a LocalDateTime to dd/MM/yyyy.
     */
    private String formatDate(java.time.LocalDateTime dateTime) {
        return Optional.ofNullable(dateTime)
                .map(DATE_FORMATTER::format)
                .orElse("N/A");
    }
}
