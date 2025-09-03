package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetDAO;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepairService {
    private final RepairRepository repairRepository;
    private final AssetService assetService;
    private final FileUpload fileUpload;
    private final StatusService statusService;
    private final AssetRepository assetRepository;

    @Transactional
    public AssetDAO createRepair(Long assetId,
                                 LocalDateTime repairStartDate,
                                 LocalDateTime repairEndDate,
                                 String technician,
                                 String repairReason,
                                 MultipartFile[] files) throws Exception {

        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID must not be null.");
        }

        Asset asset = assetService.findOne(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("Asset not found with ID: " + assetId);
        }

        final long REPAIR_STATUS_ID = 13L;
        Status repairStatus = statusService.findOneStatus(REPAIR_STATUS_ID);
        asset.setAssetStatus(repairStatus);
        assetRepository.save(asset);

        List<String> documentPaths = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String path = fileUpload.uploadFileToLocalDirectory(file);
                    documentPaths.add(path);
                }
            }
        }

        Repair repair = Repair.builder()
                .repairStartDate(repairStartDate)
                .repairEndDate(null)
                .repairReason(repairReason)
                .technician(technician)
                .documents(documentPaths)
                .asset(asset)
                .status("Pending")
                .build();

        repairRepository.save(repair);
        return assetService.buildAssetDao(asset);
    }

    @Transactional
    public Page<RepairDTO> listRepairDetails(Long assetId, Pageable pageable) {
        return repairRepository.findByAssetId(assetId, pageable)
                .map(this::convertToDTO);
    }

    private RepairDTO convertToDTO(Repair repair) {
        Asset asset = repair.getAsset();

        AssetDAO assetDAO = AssetDAO.builder()
                .id(asset.getId())
                .assetName(asset.getAssetName())
                .hostname(asset.getHostname())
                .serialNumber(asset.getSerialNumber())
                .make(asset.getMake())
                .model(asset.getModel())
                .engravedNumber(asset.getEngravedNumber())
                .assetType(asset.getAssetType() != null ?
                        AssetType.builder()
                                .id(asset.getAssetType().getId())
                                .name(asset.getAssetType().getName())
                                .description(asset.getAssetType().getDescription())
                                .build() : null)
                .build();

        return RepairDTO.builder()
                .id(repair.getId())
                .repairStartDate(repair.getRepairStartDate())
                .repairEndDate(repair.getRepairEndDate())
                .technician(repair.getTechnician())
                .repairReason(repair.getRepairReason())
                .status(repair.getStatus())
                .documents(repair.getDocuments())
                .completionNotes(repair.getCompletionNotes())
                .completionDocuments(repair.getCompletionDocuments())
                .asset(assetDAO)
                .build();
    }

    private Repair findOneRepair(Long id) throws NotFoundException {
        return repairRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("Repair with ID %s is not Found", id))
        );
    }

    public Repair completeRepair(
            Long repairId,
            LocalDateTime repairEndDate,
            String completionNotes,
            MultipartFile[] files
    ) throws NotFoundException, IOException {
        Repair repair = findOneRepair(repairId);
        Asset asset = repair.getAsset();

        final long IN_STORE_STATUS_ID = 12L; // Return the asset to store after Repair
        Status inStoreStatus = statusService.findOneStatus(IN_STORE_STATUS_ID);
        asset.setAssetStatus(inStoreStatus);
        assetRepository.save(asset);

        List<String> documentPaths = new ArrayList<>();

        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String path = fileUpload.uploadFileToLocalDirectory(file);
                    documentPaths.add(path);
                }
            }
        }
        repair.setRepairEndDate(repairEndDate);
        repair.setCompletionNotes(completionNotes);
        repair.setCompletionDocuments(documentPaths);
        repair.setStatus("Completed");
        return repairRepository.save(repair);
    }
}