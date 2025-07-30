package pride.bank.erp.issuance;

import org.springframework.stereotype.Component;
import pride.bank.erp.user.User;

@Component
public class UserBuilder {
    public User builder(User user) {
        return User.builder()
                .id(user.getId())
                .availability(user.getAvailability())
                .branch(user.getBranch())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .otherName(user.getOtherName())
                .gender(user.getGender())
                .staffNumber(user.getStaffNumber())
                .title(user.getTitle())
                .department(user.getDepartment())
                .isEnabled(user.isEnabled())
                .isAccountNonLocked(user.isAccountNonLocked())
                .blocked(user.isBlocked())
                .build();
    }
}
