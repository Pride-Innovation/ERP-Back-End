/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierRepository;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SupplierCommoditySeeder {
    private static final Logger log = LoggerFactory.getLogger(SupplierCommoditySeeder.class);
    private static final String SUPPLIER_AND_COMMODITIES_RESOURCE = "supplierAndCommodities.json";

    private final SupplierRepository supplierRepository;
    private final CommodityRepository commodityRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedSuppliersAndCommodityLinks() {
        try {
            ClassPathResource resource = new ClassPathResource(SUPPLIER_AND_COMMODITIES_RESOURCE);
            List<SupplierCommoditySeedNode> supplierNodes = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });

            seedSuppliersAndCommodityLinks(supplierNodes);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed suppliers and commodity links from " + SUPPLIER_AND_COMMODITIES_RESOURCE, exception);
        }
    }

    void seedSuppliersAndCommodityLinks(List<SupplierCommoditySeedNode> supplierNodes) {
        for (SupplierCommoditySeedNode supplierNode : supplierNodes) {
            if (supplierNode.getSupplier() == null || supplierNode.getSupplier().isBlank()) {
                continue;
            }

            Supplier supplier = findOrCreateSupplier(supplierNode.getSupplier());
            Set<Commodity> matchedCommodities = resolveCommodities(supplierNode);
            synchronizeSupplierCommodities(supplier, matchedCommodities);
            supplierRepository.save(supplier);
        }
    }

    private Supplier findOrCreateSupplier(String supplierName) {
        return supplierRepository.findByNameIgnoreCase(supplierName)
                .orElseGet(() -> supplierRepository.save(
                        Supplier.builder()
                                .name(supplierName)
                                .address(null)
                                .telephone(null)
                                .email(null)
                                .build()
                ));
    }

    private Set<Commodity> resolveCommodities(SupplierCommoditySeedNode supplierNode) {
        Set<Commodity> commodities = new LinkedHashSet<>();

        if (supplierNode.getCommodities() == null) {
            return commodities;
        }

        for (String commodityName : supplierNode.getCommodities()) {
            if (commodityName == null || commodityName.isBlank()) {
                continue;
            }

            List<Commodity> matches = commodityRepository.findAllByNameIgnoreCase(commodityName);
            if (matches.isEmpty()) {
                log.info("Skipping supplier-commodity seed for supplier '{}' because commodity '{}' was not found", supplierNode.getSupplier(), commodityName);
                continue;
            }

            commodities.addAll(matches);
        }

        return commodities;
    }

    private void synchronizeSupplierCommodities(Supplier supplier, Set<Commodity> matchedCommodities) {
        if (supplier.getCommodities() == null) {
            supplier.setCommodities(new LinkedHashSet<>());
        }

        for (Commodity existingCommodity : List.copyOf(supplier.getCommodities())) {
            if (!matchedCommodities.contains(existingCommodity)) {
                supplier.getCommodities().remove(existingCommodity);
                if (existingCommodity.getSuppliers() != null) {
                    existingCommodity.getSuppliers().remove(supplier);
                }
            }
        }

        for (Commodity commodity : matchedCommodities) {
            supplier.getCommodities().add(commodity);
            if (commodity.getSuppliers() == null) {
                commodity.setSuppliers(new LinkedHashSet<>());
            }
            commodity.getSuppliers().add(supplier);
        }
    }
}