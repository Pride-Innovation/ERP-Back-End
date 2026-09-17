/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stockReceipt;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.grnCommodity.GrnCommodity;
import pride.bank.erp.grnCommodity.GrnCommodityRepository;
import pride.bank.erp.grnReport.GrnReport;
import pride.bank.erp.grnReport.GrnReportRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Read model for the delivery history of an order — one entry per physical receipt, with the
 * lines that arrived in that batch and the GRN issued for it.
 */
@Service
@RequiredArgsConstructor
public class StockReceiptService {

    private final StockReceiptRepository stockReceiptRepository;
    private final GrnReportRepository grnReportRepository;
    private final GrnCommodityRepository grnCommodityRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<StockReceiptView> fetchReceiptsForStock(Long stockId) {
        return stockReceiptRepository.findByStockIdOrderByReceiptDateAsc(stockId).stream()
                .filter(receipt -> !Boolean.TRUE.equals(receipt.getDisposed()))
                .map(this::toView)
                .toList();
    }

    private StockReceiptView toView(StockReceipt receipt) {
        Optional<GrnReport> grn = grnReportRepository.findByStockReceiptId(receipt.getId());

        List<GrnCommodity> grnLines = grn
                .map(report -> grnCommodityRepository.findByGrnReportId(report.getId()))
                .orElseGet(List::of)
                .stream()
                .filter(line -> !Boolean.TRUE.equals(line.getDisposed()))
                .toList();

        List<StockReceiptView.Line> lines = grnLines.stream()
                .map(line -> StockReceiptView.Line.builder()
                        .commodityId(line.getCommodity().getId())
                        .commodityName(line.getCommodity().getName())
                        .quantityReceived(line.getDeliveredQuantity())
                        .orderedQuantity(line.getOrderedQuantity())
                        .cumulativeDelivered(line.getTotalDeliveredQuantity())
                        .costPrice(line.getCostPrice())
                        .purchasePrice(line.getPurchasePrice())
                        .build())
                .toList();

        return StockReceiptView.builder()
                .id(receipt.getId())
                .receiptDate(receipt.getReceiptDate())
                .deliveryNoteNumber(receipt.getDeliveryNoteNumber())
                .invoiceNumber(receipt.getInvoiceNumber())
                .notes(receipt.getNotes())
                .storeName(receipt.getStore() != null ? receipt.getStore().getName() : null)
                .grnNumber(grn.map(GrnReport::getName).orElse(null))
                .grnReportId(grn.map(GrnReport::getId).orElse(null))
                .receivedBy(receipt.getCreatedBy())
                .receivedByName(resolveUserName(receipt.getCreatedBy()))
                .totalQuantityReceived(lines.stream().mapToInt(StockReceiptView.Line::getQuantityReceived).sum())
                .lines(lines)
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(this::fullName)
                .orElse(null);
    }

    private String fullName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }
}
