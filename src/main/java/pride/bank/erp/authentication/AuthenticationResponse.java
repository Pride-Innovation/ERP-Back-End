package pride.bank.erp.authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.role.Role;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AuthenticationResponse {
    private Long id;
    private String accessToken;
    private String refreshToken;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
}
