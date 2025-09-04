package pride.bank.erp.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    public Leave createLeave(LeaveDTO request, Long userId)
            throws NotFoundException {
        User actingUser = userService.findUser(request.getActingUser());
        User userGoingOnLeave = userService.findUser(userId);

//        userGoingOnLeave.setEnabled(false);
//        userGoingOnLeave.setAccountNonLocked(false);

        actingUser.setActingTitle(userGoingOnLeave.getTitle());
        actingUser.setActing(true);

        userRepository.save(actingUser);
        userRepository.save(userGoingOnLeave);

        /*
         *  Ensure That the current user who is going on leave status to be adjusted
         *  Ensure that the Acting user is given all the roles if he or she does not already have them
         */

        Leave leave = Leave
                .builder()
                .leaveType(request.getLeaveType())
                .actingUser(actingUser)
                .endDate(request.getEndDate())
                .startDate(request.getStartDate())
                .reason(request.getReason())
                .build();

        return leaveRepository.save(leave);
    }
}
