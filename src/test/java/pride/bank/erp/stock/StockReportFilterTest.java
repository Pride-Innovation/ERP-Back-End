package pride.bank.erp.stock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeAction;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.stockCommodity.StockCommodity;
import pride.bank.erp.stockCommodity.StockCommodityRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

/**
 * The Stock Management report's filters on {@code GET /stocks}: category and the ordered period run
 * in the database, and every order says who created it.
 *
 * <p>Expectations come from the entities, never from the query under test: an order matches a
 * category when one of its lines is in it, and an order's date is its order date, else its created
 * date - the date the report's column shows.
 */
@SpringBootTest
@Transactional
class StockReportFilterTest {

    @Autowired StockService stockService;
    @Autowired StockRepository stockRepository;
    @Autowired StockCommodityRepository stockCommodityRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;
    @Autowired AccessScopeService accessScopeService;

    @BeforeEach
    void signInAsAllBranchesViewer() {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            signInAs(u);
            if (accessScopeService.scopeFor(ScopeSubject.INVENTORY, ScopeAction.VIEW) == DashboardScope.ALL) return;
        }
        org.junit.jupiter.api.Assumptions.abort("No account sees every branch's inventory");
    }

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void categoryReturnsExactlyTheOrdersWithALineInItEachOnce() {
        Map<Long, Set<Long>> categoriesByOrder = stockCommodityRepository.findAll().stream()
                .filter(l -> l.getStock() != null && !Boolean.TRUE.equals(l.getStock().getDisposed()))
                .collect(Collectors.groupingBy(l -> l.getStock().getId(),
                        Collectors.mapping(StockReportFilterTest::categoryOf, Collectors.toSet())));
        Long category = categoriesByOrder.values().stream().flatMap(Set::stream)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (category == null) {
            System.out.println("stock report test: NO categorised order line - category filter unexercised");
            return;
        }
        Set<Long> expected = categoriesByOrder.entrySet().stream()
                .filter(e -> e.getValue().contains(category)).map(Map.Entry::getKey).collect(Collectors.toSet());

        List<StockDAO> found = search(null, null, category);

        assertThat(found).extracting(StockDAO::getId).doesNotHaveDuplicates();
        assertThat(found).extracting(StockDAO::getId).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(found.size()).as("the page count agrees with the rows").isEqualTo((int) page(null, null, category).getTotalElements());
    }

    @Test
    void theOrderedPeriodUsesTheOrderDateElseTheCreatedDate() {
        List<Stock> stocks = stockRepository.findAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getDisposed())).toList();
        Stock pivot = stocks.stream().filter(s -> s.getOrderDate() != null || s.getCreateDate() != null)
                .findFirst().orElse(null);
        if (pivot == null) {
            System.out.println("stock report test: NO dated order - period unexercised");
            return;
        }
        LocalDateTime when = placed(pivot);
        LocalDateTime from = when.minusDays(10), to = when.plusDays(10);
        Set<Long> expected = stocks.stream()
                .filter(s -> placed(s) != null && !placed(s).isBefore(from) && !placed(s).isAfter(to))
                .map(Stock::getId).collect(Collectors.toSet());

        assertThat(search(from, to, null)).extracting(StockDAO::getId).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void everyOrderNamesWhoCreatedIt() {
        List<StockDAO> all = search(null, null, null);
        for (StockDAO s : all) {
            if (s.getCreatedBy() == null) continue;
            User creator = userRepository.findById(s.getCreatedBy()).orElse(null);
            if (creator == null) continue;
            String expected = ((creator.getFirstName() == null ? "" : creator.getFirstName()) + " "
                    + (creator.getLastName() == null ? "" : creator.getLastName())).trim();
            if (expected.isEmpty()) continue;
            assertThat(s.getCreatedByName()).as("order %s", s.getId()).isEqualTo(expected);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static LocalDateTime placed(Stock s) {
        return s.getOrderDate() != null ? s.getOrderDate() : s.getCreateDate();
    }

    private static Long categoryOf(StockCommodity line) {
        return line.getCommodity() != null && line.getCommodity().getAssetType() != null
                ? line.getCommodity().getAssetType().getId() : null;
    }

    private List<StockDAO> search(LocalDateTime from, LocalDateTime to, Long assetTypeId) {
        return page(from, to, assetTypeId).getContent();
    }

    private org.springframework.data.domain.Page<StockDAO> page(LocalDateTime from, LocalDateTime to, Long assetTypeId) {
        return stockService.fetchAllStocks(5000, DESCENDING_SORT_DIRECTION, 0, "id",
                null, null, null, null, false, null, null, from, to, assetTypeId);
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
