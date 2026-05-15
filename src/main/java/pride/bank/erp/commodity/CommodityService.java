/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Objects;
import java.util.Optional;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class CommodityService {
    private final CommodityRepository commodityRepository;
    private final CommoditySearchDao commoditySearchDao;
    private final AssetTypeService assetTypeService;

    public Page<Commodity> fetchAllCommodities(CommodityQueryParams params) {
        CommodityPage commodityPage = CommodityPage.builder()
                .pageSize(params.getPageSize())
                .sortDirection(determineSortDirection(params.getSortDirection()))
                .sortBy(params.getSortBy())
                .pageNumber(params.getPageNumber())
                .build();

        CommoditySearchCriteria commoditySearchCriteria = CommoditySearchCriteria
                .builder()
                .name(params.getName())
                .groupName(params.getGroupName())
                .assetTypeId(params.getAssetTypeId())
                .build();
        return commoditySearchDao.findAllWithFilters(commodityPage, commoditySearchCriteria);
    }

    @Transactional
    public Commodity createCommodity(CommodityDTO request)
            throws NotFoundException {
        AssetType assetType = assetTypeService.findOneAssetType(request.getAssetType());
        ensureNoDuplicateCommodity(request.getName(), request.getGroupName(), request.getAssetType(), null);
        Commodity commodity = Commodity.builder()
                .name(request.getName())
                .groupName(request.getGroupName())
                .assetType(assetType)
                .build();
        return commodityRepository.save(commodity);
    }

    @Transactional
    public Commodity updateCommodity(CommodityDTO request, Long id) throws NotFoundException {
        Commodity commodity = findCommodity(id);
        AssetType assetType = assetTypeService.findOneAssetType(request.getAssetType());
        ensureNoDuplicateCommodity(request.getName(), request.getGroupName(), request.getAssetType(), id);

        commodity.setName(request.getName());
        commodity.setGroupName(request.getGroupName());
        commodity.setAssetType(assetType);
        return commodityRepository.save(commodity);
    }

    public Commodity findCommodity(Long id) throws NotFoundException {
        return commodityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Commodity with ID " + id + " not found"));
    }

    @Transactional
    public DeleteResponse deleteCommodity(Long id) throws NotFoundException {
        if (!commodityRepository.existsById(id)) {
            throw new NotFoundException("Commodity with ID " + id + " not found");
        }
        commodityRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private void ensureNoDuplicateCommodity(String name, String groupName, Long assetTypeId, Long excludeId) {
        Optional<Commodity> existing = commodityRepository
                .findByNameIgnoreCaseAndGroupNameIgnoreCaseAndAssetType_Id(name, groupName, assetTypeId);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    "A commodity with name '" + name + "', group '" + groupName + "' and the same asset type already exists");
        }
    }
}
