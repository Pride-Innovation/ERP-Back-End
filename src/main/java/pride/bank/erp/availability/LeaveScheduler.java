package pride.bank.erp.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LeaveScheduler {

    private final LeaveRepository leaveRepository;
    private final UserRepository userRepository;

    /**
     * Runs every day at midnight and automatically returns users whose leave has ended.
     * Clears the acting user's acting status and marks the leave as inactive.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void processEndedLeaves() {
        LocalDateTime now = LocalDateTime.now();
        List<Leave> endedLeaves = leaveRepository.findAllActiveLeavesEnded(now);

        for (Leave leave : endedLeaves) {
            User onLeaveUser = leave.getOnLeaveUser();
            if (onLeaveUser != null) {
                onLeaveUser.setEnabled(true);
                userRepository.save(onLeaveUser);
            }

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
}
