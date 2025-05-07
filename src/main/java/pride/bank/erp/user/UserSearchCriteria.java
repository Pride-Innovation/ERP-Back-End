package pride.bank.erp.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserSearchCriteria {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
}
