/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserService;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class AssetAssignmentHistoryService {
    private final AssetAssignmentHistoryRepository assetAssignmentHistoryRepository;
    private final UserService userService;
    private final AssetService assetService;
    private final StatusService statusService;
    private final AssetAssignmentHistorySearchDao assetAssignmentHistorySearchDao;

    public Page<AssetAssignmentHistory> findAllAssetAssignmentHistory(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            Long assetId
    ) {
        try {
            AssetAssignmentHistoryPage assetAssignmentHistoryPage = AssetAssignmentHistoryPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .pageNumber(pageNumber)
                    .sortBy(sortBy)
                    .build();
            AssetAssignmentHistorySearchCriteria assetAssignmentHistorySearchCriteria
                    = AssetAssignmentHistorySearchCriteria
                    .builder()
                    .assetId(assetId)
                    .build();
            return assetAssignmentHistorySearchDao.findAllWithFilters(
                    assetAssignmentHistoryPage,
                    assetAssignmentHistorySearchCriteria
            );
        } catch (Exception e) {
            return null;
        }
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

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
