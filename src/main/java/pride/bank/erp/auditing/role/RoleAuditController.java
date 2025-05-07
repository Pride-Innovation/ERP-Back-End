package pride.bank.erp.auditing.role;

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
public class RoleAuditController {

    private final RoleAuditService roleAuditService;

    @GetMapping("/audit/roles")
    public Page<RoleAuditResponse> getRoleAuditDetails(Pageable pageable) {
        return roleAuditService.getRoleAuditDetails(pageable);
    }
}
