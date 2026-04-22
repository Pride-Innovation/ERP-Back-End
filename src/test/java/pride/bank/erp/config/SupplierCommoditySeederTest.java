/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCommoditySeederTest {
    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private CommodityRepository commodityRepository;

    @InjectMocks
    private SupplierCommoditySeeder supplierCommoditySeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedSuppliersAndCommodityLinksCreatesSupplierAndMatchedCommodityLinks() {
        supplierCommoditySeeder = new SupplierCommoditySeeder(supplierRepository, commodityRepository, objectMapper);
        Commodity cpu = Commodity.builder().id(1L).name("CPU").groupName("pieces").suppliers(new HashSet<>()).build();
        Commodity monitor = Commodity.builder().id(2L).name("Monitor").groupName("pieces").suppliers(new HashSet<>()).build();

        when(supplierRepository.findByNameIgnoreCase("Sybyl Ltd")).thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commodityRepository.findAllByNameIgnoreCase("CPU")).thenReturn(List.of(cpu));
        when(commodityRepository.findAllByNameIgnoreCase("Monitor")).thenReturn(List.of(monitor));

        supplierCommoditySeeder.seedSuppliersAndCommodityLinks(List.of(
                new SupplierCommoditySeedNode("Sybyl Ltd", List.of("CPU", "Monitor"))
        ));

        ArgumentCaptor<Supplier> supplierCaptor = ArgumentCaptor.forClass(Supplier.class);
        verify(supplierRepository, atLeastOnce()).save(supplierCaptor.capture());

        Supplier savedSupplier = supplierCaptor.getAllValues().get(supplierCaptor.getAllValues().size() - 1);
        assertEquals("Sybyl Ltd", savedSupplier.getName());
        assertEquals(2, savedSupplier.getCommodities().size());
        assertTrue(cpu.getSuppliers().contains(savedSupplier));
        assertTrue(monitor.getSuppliers().contains(savedSupplier));
    }

    @Test
    void seedSuppliersAndCommodityLinksSkipsBlankSuppliersAndUnknownCommodities() {
        supplierCommoditySeeder = new SupplierCommoditySeeder(supplierRepository, commodityRepository, objectMapper);
        Supplier existingSupplier = Supplier.builder().name("Known Supplier").commodities(new HashSet<>()).build();

        when(supplierRepository.findByNameIgnoreCase(eq("Known Supplier"))).thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commodityRepository.findAllByNameIgnoreCase("Unknown Commodity")).thenReturn(List.of());

        supplierCommoditySeeder.seedSuppliersAndCommodityLinks(List.of(
                new SupplierCommoditySeedNode(" ", List.of("CPU")),
                new SupplierCommoditySeedNode("Known Supplier", List.of("Unknown Commodity"))
        ));

        verify(supplierRepository).findByNameIgnoreCase("Known Supplier");
        assertTrue(existingSupplier.getCommodities().isEmpty());
    }
}