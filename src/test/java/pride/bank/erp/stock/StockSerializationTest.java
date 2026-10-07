package pride.bank.erp.stock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An asset carrying a stock serialises - the store pages return exactly that.
 *
 * <h2>What went wrong</h2>
 * {@code Stock.stockCommodities} serialised each line, and each line's {@code stock} pointed back,
 * so Jackson recursed until the stack overflowed and {@code GET /inventory/stores/{id}/assets}
 * failed. It had been hidden by a second bug: Lombok's hashCode on {@code Stock} walked the same set,
 * which made loading it re-enter itself and, with assertions off, come back empty - so Jackson wrote
 * {@code []}. Fixing the hashCode let the set load its real lines and exposed the loop.
 *
 * <p>Uses the application's own ObjectMapper, so it sees every module and setting a response does.
 */
@SpringBootTest
@Transactional
class StockSerializationTest {

    @Autowired AssetRepository assetRepository;
    @Autowired ObjectMapper objectMapper;

    @Test
    void anAssetWithAStockSerialisesWithItsLinesAndWithoutLooping() throws Exception {
        Asset asset = assetRepository.findAll().stream()
                .filter(a -> a.getStock() != null
                        && a.getStock().getStockCommodities() != null
                        && !a.getStock().getStockCommodities().isEmpty())
                .findFirst()
                .orElse(null);
        assumeTrue(asset != null, "no asset in this database belongs to a stock with lines");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(asset));

        JsonNode lines = json.path("stock").path("stockCommodities");
        assertThat(lines.isArray()).as("the stock's lines are still published").isTrue();
        assertThat(lines.size()).isPositive();
        assertThat(lines.get(0).has("stock"))
                .as("a line must not carry its stock - that is the loop")
                .isFalse();
    }
}
