package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepairService {
    private final RepairRepository repairRepository;
    private final AssetService assetService;
    private final FileUpload fileUpload;


    public Repair createRepair(Long assetId, RepairDTO dto, MultipartFile[] files)
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
                .repairStartDate(dto.getRepairStartDate())
                .repairReason(dto.getRepairReason())
                .technician(dto.getTechnician())
                .documents(documentPaths)
                .asset(asset)
                .build();

        return repairRepository.save(repair);
    }
}
