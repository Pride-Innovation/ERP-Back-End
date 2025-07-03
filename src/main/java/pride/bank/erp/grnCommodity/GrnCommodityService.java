package pride.bank.erp.grnCommodity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GrnCommodityService {
    private final GrnCommodityRepository grnCommodityRepository;


    public Page<GrnCommodity> fetchAllGrnCommodities(Long stockId, Pageable pageable) {
        return grnCommodityRepository.findByStockId(stockId, pageable);
    }
}
