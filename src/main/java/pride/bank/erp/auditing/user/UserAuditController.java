package pride.bank.erp.auditing.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class UserAuditController {

    private final UserAuditService userAuditService;

    @GetMapping("/audit/users")
    public Page<UserAuditResponse> getUserAuditDetails(Pageable pageable) {
        return userAuditService.getUserAuditDetails(pageable);
    }
}
