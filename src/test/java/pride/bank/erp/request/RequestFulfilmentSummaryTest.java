package pride.bank.erp.request;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScopeService;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.requestCommodity.RequestCommodityRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dashboard's Request Fulfilment: how much of what was requested this year has been delivered.
 *
 * <p>"Delivered" read a hardcoded status id - 7, called Receipt Acknowledged in a comment and
 * inMaintenance in the database, where Receipt Acknowledged is 8. So the widget reported 0% delivered
 * over 62 acknowledged requests. The expectation here is computed from the request lines and their
 * status <b>code</b>, never through the query or an id.
 */
@SpringBootTest
@Transactional
class RequestFulfilmentSummaryTest {

    @Autowired RequestService requestService;
    @Autowired RequestCommodityRepository requestCommodityRepository;
    @Autowired DashboardScopeService dashboardScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void deliveredCountsTheLinesOfRequestsWhoseReceiptWasAcknowledged() {
        Optional<User> viewer = nationalViewer();
        if (viewer.isEmpty()) {
            System.out.println("fulfilment test: NO viewer with a national dashboard scope - unexercised");
            return;
        }
        int year = LocalDate.now().getYear();
        long requested = 0, delivered = 0;
        for (RequestCommodity line : requestCommodityRepository.findAll()) {
            Request r = line.getRequest();
            if (r == null || r.getCreateDate() == null || r.getCreateDate().getYear() != year) continue;
            if (line.getCommodity() == null || line.getCommodity().getAssetType() == null) continue;
            String code = r.getStatus() != null ? r.getStatus().getStatus() : null;
            if ("requestRejected".equals(code)) continue;
            requested += line.getQuantity();
            if ("receiptAcknowledged".equals(code)) delivered += line.getQuantity();
        }

        signInAs(viewer.get());
        var summary = requestService.getCurrentYearRequestSummary(null);

        assertThat(summary.stream().mapToLong(AssetRequestSummaryDTO::totalRequested).sum())
                .as("requested, every non-rejected line this year").isEqualTo(requested);
        assertThat(summary.stream().mapToLong(AssetRequestSummaryDTO::totalDelivered).sum())
                .as("delivered, the lines of requests whose receipt was acknowledged").isEqualTo(delivered);
        System.out.println("fulfilment test: " + delivered + " of " + requested + " delivered this year");
    }

    @Test
    void noStatusIsNamedByIdInRequestService() throws Exception {
        String source = Files.readString(Path.of("src/main/java/pride/bank/erp/request/RequestService.java"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//[^\n]*", "");
        assertThat(source)
                .as("status ids depend on seed order; resolve statuses by code (trap #4)")
                .doesNotContainPattern("(?i)(acknowledged|rejected|status)\\w*\\s*=\\s*\\d+L");
    }

    /** Someone whose dashboard is the national roll-up: no branch, no owner. */
    private Optional<User> nationalViewer() {
        for (User u : userRepository.findAll()) {
            signInAs(u);
            boolean national;
            try {
                national = dashboardScopeService.resolveBranch(null) == null && dashboardScopeService.resolveOwner() == null;
            } catch (Exception e) {
                national = false;
            }
            signOut();
            if (national) return Optional.of(u);
        }
        return Optional.empty();
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
