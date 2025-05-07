package pride.bank.erp.assetType;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

@Service
@RequiredArgsConstructor
public class AssetTypeService {
    private final AssetTypeRepository assetTypeRepository;

    public Page<AssetType> fetchAllAssetTypes(Pageable pageable) {
        return assetTypeRepository.findAll(pageable);
    }

    public AssetType createAssetType(AssetType request) {
        AssetType assetType = AssetType
                .builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return assetTypeRepository.save(assetType);
    }

    public AssetType findOneAssetType(Long id) throws NotFoundException {
        return assetTypeRepository.findById(id).orElseThrow(
                () -> new NotFoundException("User with ID " + id + " not found"));
    }

    public AssetType updateAssetType(AssetType request, Long id)
            throws NotFoundException {
        AssetType assetType = findOneAssetType(id);
        assetType.setDescription(request.getDescription());
        assetType.setName(request.getName());
        return assetTypeRepository.save(assetType);
    }

    public DeleteResponse deleteAssetType(Long id) {
        if (assetTypeRepository.existsById(id)) {
            assetTypeRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }
}

