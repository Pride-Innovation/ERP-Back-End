/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final CommodityService commodityService;


    public Page<Supplier> fetchAllSuppliers(Pageable pageable) {
        return supplierRepository.findAll(pageable);
    }

    public Supplier createSupplier(SupplierDTO request)
            throws NotFoundException {

        Commodity commodity = commodityService.findCommodity(request.getCommodity());

        Supplier supplier = Supplier.builder()
                .email(request.getEmail())
                .address(request.getAddress())
                .name(request.getName())
                .telephone(request.getTelephone())
                .commodity(commodity)
                .build();
        return supplierRepository.save(supplier);
    }


    public Supplier findSupplier(Long supplierId) throws NotFoundException {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier with ID " + supplierId + " not found"));
    }

    public Supplier updateSupplier(SupplierDTO request, Long id) throws NotFoundException {
        Commodity commodity = commodityService.findCommodity(request.getCommodity());
        Supplier supplier = findSupplier(id);
        assert supplier != null;

        supplier.setAddress(request.getAddress());
        supplier.setEmail(request.getEmail());
        supplier.setName(request.getName());
        supplier.setTelephone(request.getTelephone());
        supplier.setCommodity(commodity);
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
}
