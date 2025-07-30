/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

import java.util.List;
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

    public Page<AssetAssignmentHistoryDAO> findAllAssetAssignmentHistory(
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

            Page<AssetAssignmentHistory> assetAssignmentHistoryPageResult = assetAssignmentHistorySearchDao.findAllWithFilters(
                    assetAssignmentHistoryPage,
                    assetAssignmentHistorySearchCriteria
            );
            List<AssetAssignmentHistoryDAO> dtoList = mapToDTOList(assetAssignmentHistoryPageResult.getContent());
            return new PageImpl<>(
                    dtoList,
                    assetAssignmentHistoryPageResult.getPageable(),
                    assetAssignmentHistoryPageResult.getTotalElements()
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

    private List<AssetAssignmentHistoryDAO> mapToDTOList(List<AssetAssignmentHistory> assetAssignmentHistories) {
        return assetAssignmentHistories.stream().map(this::buildAssetAssignmentHistoryDao).toList();
    }

    private AssetAssignmentHistoryDAO buildAssetAssignmentHistoryDao(AssetAssignmentHistory assetAssignmentHistory) {
        return AssetAssignmentHistoryDAO
                .builder()
                .id(assetAssignmentHistory.getId())
                .startDate(assetAssignmentHistory.getStartDate())
                .endDate(assetAssignmentHistory.getEndDate())
                .user(assetAssignmentHistory.getUser() != null ?
                        User.builder()
                                .availability(assetAssignmentHistory.getUser().getAvailability())
                                .branch(assetAssignmentHistory.getUser().getBranch())
                                .email(assetAssignmentHistory.getUser().getEmail())
                                .firstName(assetAssignmentHistory.getUser().getFirstName())
                                .lastName(assetAssignmentHistory.getUser().getLastName())
                                .otherName(assetAssignmentHistory.getUser().getOtherName())
                                .gender(assetAssignmentHistory.getUser().getGender())
                                .staffNumber(assetAssignmentHistory.getUser().getStaffNumber())
                                .title(assetAssignmentHistory.getUser().getTitle())
                                .department(assetAssignmentHistory.getUser().getDepartment())
                                .isEnabled(assetAssignmentHistory.getUser().isEnabled())
                                .isAccountNonLocked(assetAssignmentHistory.getUser().isAccountNonLocked())
                                .blocked(assetAssignmentHistory.getUser().isBlocked())
                                .build() : null)
                .asset(assetAssignmentHistory.getAsset() != null ?
                        Asset.builder()
                                .id(assetAssignmentHistory.getAsset().getId())
                                .assetName(assetAssignmentHistory.getAsset().getAssetName())
                                .hostname(assetAssignmentHistory.getAsset().getHostname())
                                .detailNetBookValue(assetAssignmentHistory.getAsset().getDetailNetBookValue())
                                .engravedNumber(assetAssignmentHistory.getAsset().getEngravedNumber())
                                .dateReceipt(assetAssignmentHistory.getAsset().getDateReceipt())
                                .make(assetAssignmentHistory.getAsset().getMake())
                                .unitOfMeasure(assetAssignmentHistory.getAsset().getUnitOfMeasure())
                                .purchaseCost(assetAssignmentHistory.getAsset().getPurchaseCost())
                                .costOfTheAsset(assetAssignmentHistory.getAsset().getCostOfTheAsset())
                                .netValueB(assetAssignmentHistory.getAsset().getNetValueB())
                                .model(assetAssignmentHistory.getAsset().getModel())
                                .serialNumber(assetAssignmentHistory.getAsset().getSerialNumber())
                                .ram(assetAssignmentHistory.getAsset().getRam())
                                .cpuSpeed(assetAssignmentHistory.getAsset().getCpuSpeed())
                                .hardDiskSize(assetAssignmentHistory.getAsset().getHardDiskSize())
                                .macAddress(assetAssignmentHistory.getAsset().getMacAddress())
                                .ipAddress(assetAssignmentHistory.getAsset().getIpAddress())
                                .interfaceType(assetAssignmentHistory.getAsset().getInterfaceType())
                                .assetDepreciationRate(assetAssignmentHistory.getAsset().getAssetDepreciationRate())
                                .description(assetAssignmentHistory.getAsset().getDescription())
                                .image(assetAssignmentHistory.getAsset().getImage())
                                .build() : null)
                .statusBefore(assetAssignmentHistory.getStatusBefore())
                .statusAfter(assetAssignmentHistory.getStatusAfter())
                .build();
    }
}
