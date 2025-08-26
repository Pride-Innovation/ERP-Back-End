package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;

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
    public Page<Repair> listRepairDetails(Long assetId, Pageable pageable) {
        return repairRepository.findByAssetId(assetId, pageable);
    }
}
