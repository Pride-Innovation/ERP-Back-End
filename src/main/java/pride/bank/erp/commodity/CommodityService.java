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
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.unitofmeasure.UnitOfMeasureService;
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
    private final UnitOfMeasureService unitOfMeasureService;

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
        String unit = requireUnit(request.getGroupName(), null);
        ensureNoDuplicateCommodity(request.getName(), unit, request.getAssetType(), null);
        Commodity commodity = Commodity.builder()
                .name(request.getName())
                .groupName(unit)
                .assetType(assetType)
                .build();
        return commodityRepository.save(commodity);
    }

    @Transactional
    public Commodity updateCommodity(CommodityDTO request, Long id) throws NotFoundException {
        Commodity commodity = findCommodity(id);
        AssetType assetType = assetTypeService.findOneAssetType(request.getAssetType());
        // A unit the commodity already carries is accepted even if since switched off.
        String unit = requireUnit(request.getGroupName(), commodity.getGroupName());
        ensureNoDuplicateCommodity(request.getName(), unit, request.getAssetType(), id);

        commodity.setName(request.getName());
        commodity.setGroupName(unit);
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

    /** A commodity's unit: required, and one from Settings → Units of Measure. */
    private String requireUnit(String raw, String current) {
        String unit = unitOfMeasureService.resolve(raw, current);
        if (unit == null) throw new BadRequestException("Choose the commodity's unit of measure.");
        return unit;
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
