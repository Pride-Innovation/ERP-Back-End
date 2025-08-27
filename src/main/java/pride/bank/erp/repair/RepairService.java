package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetDAO;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;

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

    @Transactional
    public Repair createRepair(Long assetId,
                               LocalDateTime repairStartDate,
                               LocalDateTime repairEndDate,
                               String technician,
                               String repairReason,
                               MultipartFile[] files)
            throws Exception {
        Asset asset = assetService.findOne(assetId);
        if (asset == null) {
            throw new NotFoundException("Asset not found with ID: " + assetId);
        }

        List<String> documentPaths = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String path = fileUpload.uploadFileToLocalDirectory(file);
                    documentPaths.add(path);
                }
            }
        }

        Repair repair = Repair.builder()
                .repairStartDate(repairStartDate)
                .repairReason(repairReason)
                .technician(technician)
                .documents(documentPaths)
                .asset(asset)
                .build();

        return repairRepository.save(repair);
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
        return repairRepository.save(repair);
    }
}