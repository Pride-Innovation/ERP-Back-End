package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

@Service
@RequiredArgsConstructor
public class AssetAssignmentHistoryService {
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    private final UserService userService;
    private final AssetService assetService;
    private final StatusService statusService;

    public Page<AssetAssignmentHistory> findAllAssetAssignmentHistory(Pageable pageable) {
        return assetAssignmentHistoryRepository.findAll(pageable);
    }

    @Transactional
    public AssetAssignmentHistory creatAssetAssignmentHistory(AssetAssignmentHistoryDTO assetAssignmentHistoryDTO)
            throws NotFoundException {
        User user = userService.findUser(assetAssignmentHistoryDTO.getUserId());
        Asset asset = assetService.findOne(assetAssignmentHistoryDTO.getAssetId());
        Status statusBefore = statusService.findOneStatus(assetAssignmentHistoryDTO.getStatusBeforeId());
        Status statusAfter = statusService.findOneStatus(assetAssignmentHistoryDTO.getStatusAfterId());

        AssetAssignmentHistory assetAssignmentHistory = AssetAssignmentHistory
                .builder()
                .asset(asset)
                .endDate(assetAssignmentHistoryDTO.getEndDate())
                .startDate(assetAssignmentHistoryDTO.getStartDate())
                .statusBefore(statusBefore)
                .statusAfter(statusAfter)
                .user(user)
                .build();

        return assetAssignmentHistoryRepository.save(assetAssignmentHistory);
    }
}
