package pride.bank.erp.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;
import pride.bank.erp.user.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaveService {
    private final LeaveRepository leaveRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public List<Leave> findAllLeave() {
        return leaveRepository.findAll();
    }

    @Transactional
    public Leave createLeave(LeaveDTO request, Long userId)
            throws NotFoundException {

        if (request == null) {
            throw new IllegalArgumentException("Leave request cannot be null.");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User going on leave ID must be provided.");
        }

        User userGoingOnLeave = userService.findUser(userId);
        if (userGoingOnLeave == null) {
            throw new NotFoundException("User going on leave not found with ID: " + userId);
        }

        User actingUser = null;
        if (request.getActingUser() != null) {
            actingUser = userService.findUser(request.getActingUser());
            if (actingUser == null) {
                throw new NotFoundException("Acting user not found with ID: " + request.getActingUser());
            }

            actingUser.setActingTitle(userGoingOnLeave.getTitle());
            actingUser.setActing(true);
            userRepository.save(actingUser);
        }

        userGoingOnLeave.setAccountNonLocked(false);
        userRepository.save(userGoingOnLeave);

        Leave leave = Leave.builder()
                .leaveType(request.getLeaveType())
                .actingUser(actingUser)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .build();

        return leaveRepository.save(leave);
    }
}
