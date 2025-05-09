package pride.bank.erp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Objects;

@Service
public class CurrentUserService {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    public User getCurrentUser() {
        HttpServletRequest request =
                ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes()))
                        .getRequest();

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String jwt = authHeader.substring(7);
        String email = jwtService.extractUserEmail(jwt);
        if (email == null) {
            return null;
        }

        return userRepository.findByEmail(email).orElse(null);
    }
}
