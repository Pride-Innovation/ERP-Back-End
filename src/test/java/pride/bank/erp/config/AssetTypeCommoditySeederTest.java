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
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetTypeCommoditySeederTest {
    @Mock
    private AssetTypeRepository assetTypeRepository;

    @Mock
    private CommodityRepository commodityRepository;

    @InjectMocks
    private AssetTypeCommoditySeeder assetTypeCommoditySeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedAssetTypesAndCommoditiesCreatesMissingRecords() {
        assetTypeCommoditySeeder = new AssetTypeCommoditySeeder(assetTypeRepository, commodityRepository, objectMapper);
        List<AssetTypeSeedNode> assetTypes = List.of(
                new AssetTypeSeedNode(
                        "Equipment",
                        "Equipment (E)",
                        "E",
                        List.of(new CommoditySeedNode("Note counter", "pieces"))
                )
        );

        AssetType savedAssetType = AssetType.builder().id(1L).name("Equipment").description("Equipment (E)").shortCode("E").build();

        when(assetTypeRepository.findByShortCode("E")).thenReturn(Optional.empty());
        when(assetTypeRepository.findByNameIgnoreCase("Equipment")).thenReturn(Optional.empty());
        when(assetTypeRepository.save(any(AssetType.class))).thenReturn(savedAssetType);
        when(commodityRepository.findByNameIgnoreCaseAndGroupNameIgnoreCaseAndAssetType_Id("Note counter", "pieces", 1L))
                .thenReturn(Optional.empty());
        when(commodityRepository.save(any(Commodity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assetTypeCommoditySeeder.seedAssetTypesAndCommodities(assetTypes);

        ArgumentCaptor<Commodity> commodityCaptor = ArgumentCaptor.forClass(Commodity.class);
        verify(commodityRepository, atLeastOnce()).save(commodityCaptor.capture());

        Commodity savedCommodity = commodityCaptor.getValue();
        assertEquals("Note counter", savedCommodity.getName());
        assertEquals("pieces", savedCommodity.getGroupName());
        assertSame(savedAssetType, savedCommodity.getAssetType());
    }

    @Test
    void seedAssetTypesAndCommoditiesUpdatesExistingAssetTypeWithoutDuplicatingCommodity() {
        assetTypeCommoditySeeder = new AssetTypeCommoditySeeder(assetTypeRepository, commodityRepository, objectMapper);
        List<AssetTypeSeedNode> assetTypes = List.of(
                new AssetTypeSeedNode(
                        "Computers",
                        "Computers (C)",
                        "C",
                        List.of(
                                new CommoditySeedNode("Tablet", "pieces"),
                                new CommoditySeedNode("Tablet", "pieces")
                        )
                )
        );

        AssetType existingAssetType = AssetType.builder()
                .id(2L)
                .name("Old Computers")
                .description("Old")
                .shortCode("C")
                .build();

        when(assetTypeRepository.findByShortCode("C")).thenReturn(Optional.of(existingAssetType));
        when(assetTypeRepository.save(any(AssetType.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commodityRepository.findByNameIgnoreCaseAndGroupNameIgnoreCaseAndAssetType_Id("Tablet", "pieces", 2L))
                .thenReturn(Optional.empty());
        when(commodityRepository.save(any(Commodity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assetTypeCommoditySeeder.seedAssetTypesAndCommodities(assetTypes);

        assertEquals("Computers", existingAssetType.getName());
        assertEquals("Computers (C)", existingAssetType.getDescription());
        verify(commodityRepository, times(1)).save(any(Commodity.class));
        verify(commodityRepository).findByNameIgnoreCaseAndGroupNameIgnoreCaseAndAssetType_Id(eq("Tablet"), eq("pieces"), eq(2L));
    }
}