/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final CommodityService commodityService;
    private final SupplierSearchDao supplierSearchDao;

    public Page<Supplier> fetchAllSuppliers(SupplierQueryParams params) {
        try {
            SupplierPage supplierPage = SupplierPage.builder()
                    .pageSize(params.getPageSize())
                    .sortDirection(determineSortDirection(params.getSortDirection()))
                    .pageNumber(params.getPageNumber())
                    .sortBy(params.getSortBy())
                    .build();
            SupplierSearchCriteria supplierSearchCriteria = SupplierSearchCriteria
                    .builder()
                    .name(params.getName())
                    .build();
            return supplierSearchDao.findAllWithFilters(supplierPage, supplierSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public Supplier createSupplier(SupplierDTO request)
            throws NotFoundException {
        Set<Commodity> commodities = resolveRequestedCommodities(request);

        Supplier supplier = Supplier.builder()
                .email(request.getEmail())
                .address(request.getAddress())
                .name(request.getName())
                .telephone(request.getTelephone())
                .build();
        synchronizeSupplierCommodities(supplier, commodities);
        return supplierRepository.save(supplier);
    }


    public Supplier findSupplier(Long supplierId) throws NotFoundException {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier with ID " + supplierId + " not found"));
    }

    public Supplier updateSupplier(SupplierDTO request, Long id) throws NotFoundException {
        Set<Commodity> commodities = resolveRequestedCommodities(request);
        Supplier supplier = findSupplier(id);

        supplier.setAddress(request.getAddress());
        supplier.setEmail(request.getEmail());
        supplier.setName(request.getName());
        supplier.setTelephone(request.getTelephone());
        synchronizeSupplierCommodities(supplier, commodities);
        return supplierRepository.save(supplier);
    }

    public DeleteResponse deleteSupplier(Long supplierId) {
        if (supplierRepository.existsById(supplierId)) {
            supplierRepository.deleteById(supplierId);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private Set<Commodity> resolveRequestedCommodities(SupplierDTO request) throws NotFoundException {
        Set<Commodity> commodities = new LinkedHashSet<>();

        if (request.getCommodities() != null) {
            for (Long commodityId : request.getCommodities()) {
                if (commodityId != null) {
                    commodities.add(commodityService.findCommodity(commodityId));
                }
            }
        }

        if (request.getCommodity() != null) {
            commodities.add(commodityService.findCommodity(request.getCommodity()));
        }

        return commodities;
    }

    private void synchronizeSupplierCommodities(Supplier supplier, Set<Commodity> commodities) {
        if (supplier.getCommodities() == null) {
            supplier.setCommodities(new LinkedHashSet<>());
        }

        for (Commodity existingCommodity : List.copyOf(supplier.getCommodities())) {
            if (!commodities.contains(existingCommodity)) {
                supplier.getCommodities().remove(existingCommodity);
                existingCommodity.getSuppliers().remove(supplier);
            }
        }

        for (Commodity commodity : commodities) {
            supplier.getCommodities().add(commodity);
            if (commodity.getSuppliers() == null) {
                commodity.setSuppliers(new LinkedHashSet<>());
            }
            commodity.getSuppliers().add(supplier);
        }
    }
}
