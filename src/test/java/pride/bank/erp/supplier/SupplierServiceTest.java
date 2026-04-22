/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityService;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {
    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private CommodityService commodityService;

    @Mock
    private SupplierSearchDao supplierSearchDao;

    @InjectMocks
    private SupplierService supplierService;

    @Test
    void createSupplierAcceptsMultipleCommodityIds() throws Exception {
        Commodity cpu = Commodity.builder().id(1L).name("CPU").suppliers(new HashSet<>()).build();
        Commodity monitor = Commodity.builder().id(2L).name("Monitor").suppliers(new HashSet<>()).build();
        SupplierDTO request = SupplierDTO.builder()
                .name("Sybyl Ltd")
                .commodities(List.of(1L, 2L))
                .build();

        when(commodityService.findCommodity(1L)).thenReturn(cpu);
        when(commodityService.findCommodity(2L)).thenReturn(monitor);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Supplier savedSupplier = supplierService.createSupplier(request);

        assertEquals(2, savedSupplier.getCommodities().size());
        assertTrue(cpu.getSuppliers().contains(savedSupplier));
        assertTrue(monitor.getSuppliers().contains(savedSupplier));
    }
}