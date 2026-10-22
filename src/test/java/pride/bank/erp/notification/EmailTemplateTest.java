package pride.bank.erp.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.request.CommodityWithQuantityDTO;
import pride.bank.erp.request.Request;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.user.User;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Every email the application sends, rendered once with sample data.
 *
 * <h2>What was wrong</h2>
 * Seven templates were built by hand and had drifted into seven looks - four card widths, different
 * headers, buttons, emoji, three "automated message" boxes naming three different systems, a footer
 * stamped "© 2025" - and they relied on CSS that Outlook and Gmail drop (gradients, flexbox, a
 * {@code filter} on the logo). They now all render through {@link EmailLayout}.
 *
 * <p>The previews are written to {@code target/email-previews/} so they can be opened in a browser.
 */
class EmailTemplateTest {

    private Email email;

    @BeforeEach
    void setUp() {
        email = new Email(mock(MailDispatcher.class));
        ReflectionTestUtils.setField(email, "verificationUrl", "https://erp.example");
        ReflectionTestUtils.setField(email, "tokenExpirationTime", 3_600_000L);
    }

    @Test
    void everyTemplateSharesOneFrameAndNamesTheApplication() throws Exception {
        Map<String, String> rendered = renderAll();
        assertThat(rendered).hasSize(10);

        Path out = Path.of("target", "email-previews");
        Files.createDirectories(out);
        for (Map.Entry<String, String> e : rendered.entrySet()) {
            Files.writeString(out.resolve(e.getKey() + ".html"), e.getValue());

            String html = e.getValue();
            assertThat(html).as(e.getKey()).contains("Assets Management");
            assertThat(html).as(e.getKey()).contains("cid:logoImage");
            assertThat(html).as(e.getKey()).contains("max-width:600px");
            assertThat(html).as(e.getKey()).contains("Please do not reply to this email.");
            // CSS the major mail clients strip - the reason the old templates rendered differently per client.
            assertThat(html).as(e.getKey()).doesNotContain("linear-gradient", "display:flex", "display: flex",
                    "display:grid", "display: grid", "filter:", "filter :", "box-shadow");
            // The hardcoded year is gone; the footer reads the current one.
            assertThat(html).as(e.getKey()).doesNotContain("© 2025", "&copy; 2025 Pride");
            // No quote inside a single-quoted style attribute - it ends the attribute and drops the rest.
            assertThat(html).as(e.getKey()).doesNotContainPattern("style='[^']*:'");
            // Exactly one masthead - no template can add its own.
            assertThat(html.split("cid:logoImage", -1)).as(e.getKey()).hasSize(2);
        }
    }

    /** A name or a comment typed by a person is text, not markup. */
    @Test
    void valuesFromPeopleAreEscaped() {
        String html = email.getWorkflowEmailContent("Jane", "Request Rejected",
                "<script>alert(1)</script> & more", request(), "REJECTED");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; more");
    }

    /** A completion notice used to fall through to the default and arrive labelled "Action required". */
    @Test
    void aCompletionIsNotLabelledAsAnAction() {
        String html = email.getWorkflowEmailContent("Team", "Issuance completed",
                "Issuance has been approved.", request(), "COMPLETION");
        assertThat(html).contains("Completed").doesNotContain("Action required");
    }

    /** The router matches a path segment; the workflow mail used to send ?id=, which opened nothing. */
    @Test
    void requestLinksUseThePathTheRouterMatches() {
        String workflow = email.getWorkflowEmailContent("Jane", "Approval Required", "Please review.", request(), "PENDING");
        String requestMail = email.getRequestEmailContent(request(), "Jane", "View Request", null);
        assertThat(workflow).contains("https://erp.example/assets-mgt/asset-request/view/37").doesNotContain("view?id=");
        assertThat(requestMail).contains("https://erp.example/assets-mgt/asset-request/view/37");
        assertThat(email.getStockEmailContent(stock(), "Dear Computers Team,", "View Stock", "Items received."))
                .contains("https://erp.example/assets-mgt/inventory/view/12");
    }

    /** "For your information" copies carry no button - there is nothing for the reader to do. */
    @Test
    void anInformationCopyHasNoButton() {
        String html = email.getWorkflowEmailContent("Team", "Request Update", "FYI.", request(), "CC");
        assertThat(html).doesNotContain("View request");
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private Map<String, String> renderAll() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("account-verification", email.getAuthenticationEmailContent("tok", "5 Oct 2026, 17:00",
                "Account Verification", "Verify My Account", "Xk2#pL9q", "Jane Akello"));
        out.put("password-reset", email.getAuthenticationEmailContent("tok", "5 Oct 2026, 17:00",
                "Reset Your Password", "Reset Password", null, "reset"));
        out.put("request-awaiting-action", email.getRequestEmailContent(request(), "Moses", "View Request", null));
        out.put("request-update", email.getRequestEmailContent(request(), "Jane Akello", "View Request",
                "Your items have been issued and are on their way."));
        out.put("stock-received", email.getStockEmailContent(stock(), "Dear Computers Team,", "View Stock",
                "Kindly proceed to <strong>update the inventory</strong>.<br/>- 3 HP EliteBook 840<br/>"));
        out.put("asset-assigned", email.getNotificationContent("Jane", "HP EliteBook 840 (PBL-2026-LPT07)"));
        out.put("acting-officer", email.getLeaveEmailContent("Moses", "Jane", "Annual", "Branch Manager",
                "6 Oct 2026", "20 Oct 2026", "Family commitments"));
        out.put("workflow-pending", email.getWorkflowEmailContent("Moses", "Manager Approval Required",
                "Please review and approve or reject this request in the system.", request(), "PENDING"));
        out.put("movement-approval", email.getMovementEmailContent("Moses", 70L, "Transfer",
                "Gulu Admin Store → Head Office IT Store", "https://erp.example/assets-mgt/movement/view/70",
                false, false, null));
        out.put("stock-count-approval", email.getStockCountApprovalEmailContent("Moses", "SC/2026/000014",
                "Gulu Admin Store", "Gulu Branch", "Jane Akello", 18, 2,
                "https://erp.example/assets-mgt/store/stock-take/14"));
        return out;
    }

    /** Names in the stock-count mail are text, and are escaped exactly once. */
    @Test
    void theStockCountMailEscapesOnceAndOpensTheCount() {
        String html = email.getStockCountApprovalEmailContent("Moses", "SC/2026/000014",
                "Tom & Jerry <Store>", "Gulu Branch", "Jane", 3, 0, "https://erp.example/assets-mgt/store/stock-take/14");
        assertThat(html).contains("Tom &amp; Jerry &lt;Store&gt;").doesNotContain("&amp;amp;")
                .contains("https://erp.example/assets-mgt/store/stock-take/14")
                .contains("Approval required");
    }

    private static Request request() {
        Branch branch = new Branch();
        branch.setName("Gulu Branch");
        User requester = new User();
        requester.setFirstName("Jane");
        requester.setLastName("Akello");
        requester.setBranch(branch);

        Commodity laptop = new Commodity();
        laptop.setName("HP EliteBook 840");
        laptop.setGroupName("Pieces");
        Commodity toner = new Commodity();
        toner.setName("Toner cartridge 85A");
        toner.setGroupName("Pieces");

        Request request = new Request();
        request.setId(37L);
        request.setName("Laptops for new tellers");
        request.setPriority("High");
        request.setRequester(requester);
        request.setCommodities(List.of(
                new CommodityWithQuantityDTO(laptop, 2),
                new CommodityWithQuantityDTO(toner, 5)));
        return request;
    }

    private static Stock stock() {
        Branch branch = new Branch();
        branch.setName("Head Office");
        Supplier supplier = new Supplier();
        supplier.setName("Computer Point Ltd");
        Stock stock = new Stock();
        stock.setId(12L);
        stock.setLpoNumber("LPO-2026-0042");
        stock.setSupplier(supplier);
        stock.setBranch(branch);
        stock.setCreateDate(LocalDateTime.of(2026, 10, 5, 9, 30));
        return stock;
    }
}
