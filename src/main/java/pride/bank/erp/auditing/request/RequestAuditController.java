package pride.bank.erp.auditing.request;

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
public class RequestAuditController {
    private final RequestAuditService requestAuditService;

    @GetMapping("/audit/requests")
    public Page<RequestAuditResponse> getRequestsAuditDetails(Pageable pageable) {
        return requestAuditService.getRequestAuditDetails(pageable);
    }
}
