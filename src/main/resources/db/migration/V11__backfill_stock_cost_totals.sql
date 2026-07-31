-- Recomputes stock.total_cost / stock.balance_cost from their commodity lines.
--
-- The stock form captures a UNIT price per line, but the client summed those unit prices without
-- multiplying by quantity, and the server stores the client's figure verbatim (StockService assigns
-- request.getTotalCost() with no validation). Every stock created before this therefore understates
-- its own value — e.g. 2 x 5,000,000 + 3 x 6,000,000 was stored as 11,000,000 instead of 28,000,000
-- — and that figure is not cosmetic: it is echoed by the inventory export, reported against every
-- asset created from the stock, and printed on the signed GRN as "Total Cost" / "Balance Cost".
--
-- Quantity here is ORDERED, not delivered, matching the corrected client: the total states what the
-- order commits to, so it stays stable while goods arrive in batches. A delivered-based total would
-- drop on a partial delivery and never recover, because receiving a top-up does not recalculate it.
--
-- Only stocks that actually have commodity lines are touched; a stock with none keeps its existing
-- value rather than being zeroed. COALESCE guards lines whose unit price was never captured.
--
-- stock_aud is deliberately NOT rewritten. It is the Envers audit trail: those rows record what the
-- application genuinely stored at each revision, and rewriting them would falsify history.

UPDATE stock s
JOIN (
    SELECT sc.stock_id,
           SUM(COALESCE(sc.cost_price, 0)     * sc.ordered_quantity) AS correct_total_cost,
           SUM(COALESCE(sc.purchase_price, 0) * sc.ordered_quantity) AS correct_balance_cost
    FROM stock_commodity sc
    GROUP BY sc.stock_id
) line_totals ON line_totals.stock_id = s.id
SET s.total_cost   = line_totals.correct_total_cost,
    s.balance_cost = line_totals.correct_balance_cost;
