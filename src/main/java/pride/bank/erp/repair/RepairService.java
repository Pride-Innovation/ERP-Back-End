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
import pride.bank.erp.assetType.RepairDestination;
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
@lombok.extern.slf4j.Slf4j
public class RepairService {

    /**
     * The two states a maintenance record has, and the asset status a finished repair leaves behind.
     *
     * <p>Named rather than written inline at four call sites: {@code "Pending"} is what
     * {@code findFirstByAsset_IdAndStatusOrderByIdDesc} compares against and what the maintenance
     * report filters on, so a typo in one of them would silently stop the return leg finding the
     * record the transfer opened.
     */
    private static final String STATUS_PENDING = "Pending";
    private static final String STATUS_COMPLETED = "Completed";
    private static final String CODE_SENT_TO_STORE = "sentToStore";

    private final RepairRepository repairRepository;
    private final AssetService assetService;
    private final FileUpload fileUpload;
    private final StatusService statusService;
    private final AssetRepository assetRepository;
    private final pride.bank.erp.auditing.trail.AuditService auditService;
    private final pride.bank.erp.user.UserRepository userRepository;
    private final pride.bank.erp.consultant.ConsultantRepository consultantRepository;

    /** The one resolver for how far a person may see — see AccessScopeService. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    /** Records a maintenance event, tagged to the asset rather than the repair record. */
    private void audit(pride.bank.erp.auditing.trail.AuditEventType eventType, String action,
                       Asset asset, String description) {
        auditService.record(eventType, pride.bank.erp.auditing.trail.AuditModule.MAINTENANCE,
                action, description, pride.bank.erp.auditing.trail.AuditSeverity.INFO,
                "Asset", asset.getId());
    }

    /** "Dell Latitude 5540 (PBL/IT/0412)" — the tag is how staff identify an item. */
    private static String describe(Asset asset) {
        String name = asset.getAssetName() == null ? "asset" : asset.getAssetName();
        String tag = asset.getEngravedNumber();
        return (tag == null || tag.isBlank()) ? name + " (#" + asset.getId() + ")" : name + " (" + tag + ")";
    }

    /**
     * Opens a maintenance record for an asset that is being sent away to be fixed.
     *
     * <h2>Why this is called from the movement flow rather than being a flow of its own</h2>
     * There used to be a second, independent repair path - {@code POST /assets/repairs/}{id} behind
     * the assets page's "Repair" row option - which wrote this record and nothing else: no movement,
     * no custody hand-over, no approval, and no branch scope. Meanwhile the movements page's repair
     * transfer moved the asset properly and wrote <em>no</em> maintenance record. Measured before
     * these were joined: <b>24 repair movements and 0 repair rows</b>, which is why the maintenance
     * report and the asset's Repair History tab were empty for every repair the bank had done.
     *
     * <p>So the record is now opened by the act that sends the asset away, and closed by the act
     * that brings it back. One repair, one journey, one record.
     *
     * <p><b>This does not touch the asset's status.</b> {@code RepairMovementService} owns that, and
     * resolves it by code. The old path set it here from a hardcoded id - see
     * {@link #completeRepair} for what that cost.
     */
    @Transactional
    public Repair openForTransfer(Asset asset, String faultDescription, RepairDestination routing,
                                  TechnicianSelectionDTO technician, Long routingConsultantId,
                                  List<String> documents) throws NotFoundException {
        if (asset == null) {
            throw new IllegalArgumentException("An asset is required to open a repair record.");
        }
        if (faultDescription == null || faultDescription.isBlank()) {
            throw new IllegalArgumentException(
                    "Describe what is wrong with this asset before sending it for repair.");
        }

        Repair draft = Repair.builder()
                .repairStartDate(LocalDateTime.now())
                .repairEndDate(null)
                .repairReason(faultDescription.trim())
                .repairDestination(routing)
                .documents(documents == null ? new ArrayList<>() : new ArrayList<>(documents))
                .asset(asset)
                .status(STATUS_PENDING)
                .build();

        /*
         * An external repair's technician is the vendor it is going to, by construction - the form
         * has already chosen it once, for the routing. Recorded from that choice, and a different
         * technician refused rather than quietly overwritten: two vendors on one repair would mean
         * either the routing or the technician is wrong, and the form cannot say which.
         */
        if (routing == RepairDestination.EXTERNAL && routingConsultantId != null) {
            if (technician != null && !technician.isEmpty()
                    && !(technician.getTechnicianType() == TechnicianType.EXTERNAL
                    && routingConsultantId.equals(technician.getTechnicianConsultantId()))) {
                throw new IllegalArgumentException(
                        "An external repair is worked on by the consultant it is sent to. "
                                + "Choose the technician through the consultant, not separately.");
            }
            technician = TechnicianSelectionDTO.builder()
                    .technicianType(TechnicianType.EXTERNAL)
                    .technicianConsultantId(routingConsultantId)
                    .build();
        }
        if (technician != null && !technician.isEmpty()) {
            applyTechnician(draft, technician);
        }

        Repair repair = repairRepository.save(draft);

        audit(pride.bank.erp.auditing.trail.AuditEventType.CREATED, "REPAIR_RAISED", asset,
                "Sent " + describe(asset) + " for repair"
                        + (repair.getTechnician() == null ? "" : " by " + repair.getTechnician())
                        + " — " + repair.getRepairReason());
        return repair;
    }

    /**
     * Closes the record {@link #openForTransfer} opened, as the asset is sent back.
     *
     * <p><b>Deliberately tolerant of there being nothing to close.</b> Seven repaired assets were
     * returned before the two flows were joined and have no maintenance record at all; refusing
     * their return for want of one would strand them at Head Office to fix a reporting gap. A
     * missing record is logged and the return proceeds.
     *
     * <p>Like its opposite number, it leaves the asset's status alone - the return movement carries
     * {@code restoreStatusCode}, which puts back exactly what the asset read before the fault.
     *
     * @return the record that was closed, or null when there was none
     */
    @Transactional
    public Repair closeForReturn(Asset asset, String completionNotes, List<String> documents,
                                 TechnicianSelectionDTO technician) throws NotFoundException {
        if (asset == null) return null;

        Repair repair = findOpenRepair(asset);
        if (repair == null) {
            log.info("Asset {} is being returned from repair with no open maintenance record to "
                    + "close - it predates the repair record being opened by the transfer",
                    asset.getId());
            return null;
        }

        requireTechnicianForCompletion(repair, technician);
        applyCompletion(repair, LocalDateTime.now(), completionNotes, documents);
        return repairRepository.save(repair);
    }

    /** The maintenance record still open on this asset, if any. */
    @Transactional(readOnly = true)
    public Repair findOpenRepair(Asset asset) {
        if (asset == null) return null;
        return repairRepository
                .findFirstByAsset_IdAndStatusOrderByIdDesc(asset.getId(), STATUS_PENDING)
                .orElse(null);
    }

    // ── Technicians ──────────────────────────────────────────────────────────

    /**
     * Records who worked on a repair, at any point in its life - including after it is closed, to
     * correct a mistake.
     *
     * <p>Optional when the repair is raised, because the bench decides once the asset arrives; the
     * completion paths then require it (see {@link #requireTechnicianForCompletion}). This is the
     * third way in, for the days between.
     */
    @Transactional
    public RepairDTO assignTechnician(Long repairId, TechnicianSelectionDTO technician)
            throws NotFoundException {
        Repair repair = findOneRepair(repairId);
        requireAssetInScope(repair.getAsset());
        if (technician == null || technician.isEmpty()) {
            throw new IllegalArgumentException("Choose the technician: an internal officer or an external consultant.");
        }
        applyTechnician(repair, technician);
        Repair saved = repairRepository.save(repair);
        audit(pride.bank.erp.auditing.trail.AuditEventType.UPDATED, "REPAIR_TECHNICIAN_SET",
                saved.getAsset(), "Recorded " + saved.getTechnician() + " as the technician on the repair of "
                        + describe(saved.getAsset()));
        return convertToDTO(saved);
    }

    /**
     * The officers who may be recorded as an internal technician on a repair with this routing.
     *
     * <p>IT repairs are serviced by the Infra unit and Admin repairs by the Admin unit, so the list
     * follows the routing. The unit is found by the role it confers, not by name.
     *
     * <p>Deliberately <b>not</b> branch-scoped, unlike {@code /users/picker}. Both units sit at Head
     * Office and service the whole bank, so a Gulu officer raising a repair must be able to name a
     * Head Office technician - the branch-scoped picker would have offered them nobody.
     *
     * <p>An EXTERNAL repair has no internal technicians: it is worked on by the vendor it went to.
     */
    @Transactional(readOnly = true)
    public List<pride.bank.erp.user.StaffOptionDTO> listInternalTechnicians(RepairDestination routing) {
        String role = unitRoleFor(routing);
        if (role == null) return List.of();
        return userRepository.findActiveMembersOfUnitsWithRole(role).stream()
                .map(pride.bank.erp.user.StaffOptionDTO::from)
                .toList();
    }

    /**
     * The one place a technician is validated and written.
     *
     * <p>Refuses rather than guesses. A kind with nothing chosen, or a reference of the wrong kind,
     * would otherwise be stored as half a technician; and an internal officer outside the unit that
     * services this routing would make the record say a facilities officer repaired a laptop.
     */
    void applyTechnician(Repair repair, TechnicianSelectionDTO selection) throws NotFoundException {
        TechnicianType type = selection.getTechnicianType();
        if (type == null) {
            throw new IllegalArgumentException(
                    "Say whether the technician is an internal officer or an external consultant.");
        }

        RepairDestination routing = destinationOf(repair);
        switch (type) {
            case INTERNAL -> {
                if (selection.getTechnicianUserId() == null || selection.getTechnicianConsultantId() != null) {
                    throw new IllegalArgumentException("An internal technician is chosen from the staff list only.");
                }
                if (routing == RepairDestination.EXTERNAL) {
                    throw new IllegalArgumentException(
                            "This repair was sent to an external consultant, so it cannot have an internal technician.");
                }
                pride.bank.erp.user.User officer = userRepository.findById(selection.getTechnicianUserId())
                        .orElseThrow(() -> new NotFoundException(
                                "User " + selection.getTechnicianUserId() + " not found"));
                boolean eligible = userRepository.findActiveMembersOfUnitsWithRole(unitRoleFor(routing))
                        .stream().anyMatch(u -> u.getId().equals(officer.getId()));
                if (!eligible) {
                    throw new IllegalArgumentException(String.format(
                            "%s is not an active member of the %s unit, which services %s repairs.",
                            personName(officer),
                            routing == RepairDestination.ADMIN ? "Admin" : "Infra",
                            routing == RepairDestination.ADMIN ? "Admin" : "IT"));
                }
                repair.setTechnicianType(TechnicianType.INTERNAL);
                repair.setTechnicianUser(officer);
                repair.setTechnicianConsultant(null);
                repair.setTechnician(personName(officer));
            }
            case EXTERNAL -> {
                if (selection.getTechnicianConsultantId() == null || selection.getTechnicianUserId() != null) {
                    throw new IllegalArgumentException(
                            "An external technician is chosen from the repair consultants only.");
                }
                pride.bank.erp.consultant.Consultant consultant = consultantRepository
                        .findById(selection.getTechnicianConsultantId())
                        .orElseThrow(() -> new NotFoundException(
                                "Consultant " + selection.getTechnicianConsultantId() + " not found"));
                // An inactive consultant stays on the repairs they already did - only a new choice
                // must be somebody the bank still uses.
                boolean unchanged = repair.getTechnicianConsultant() != null
                        && consultant.getId().equals(repair.getTechnicianConsultant().getId());
                if (!consultant.isActive() && !unchanged) {
                    throw new IllegalArgumentException(
                            consultant.getName() + " is no longer an active repair consultant.");
                }
                repair.setTechnicianType(TechnicianType.EXTERNAL);
                repair.setTechnicianConsultant(consultant);
                repair.setTechnicianUser(null);
                repair.setTechnician(consultant.getName());
            }
        }
    }

    /**
     * A finished repair names who did it.
     *
     * <p>Optional while the repair is open; required by the time it closes, on either path - the
     * bench completion and the return. A selection sent now wins; otherwise one recorded earlier is
     * enough. Records from before V26 carry typed text and no reference, and that text is accepted:
     * refusing the return of an asset already repaired, for want of a dropdown that did not exist
     * when it was sent, would strand it at Head Office.
     */
    private void requireTechnicianForCompletion(Repair repair, TechnicianSelectionDTO selection)
            throws NotFoundException {
        if (selection != null && !selection.isEmpty()) {
            applyTechnician(repair, selection);
            return;
        }
        boolean recorded = repair.getTechnicianType() != null
                || (repair.getTechnician() != null && !repair.getTechnician().isBlank());
        if (!recorded) {
            throw new IllegalArgumentException(
                    "Record who repaired this asset - an internal officer or an external consultant - "
                            + "before closing the repair.");
        }
    }

    /**
     * Where a repair went. Stored since V26; for an older record with no transfer to read it from,
     * the asset category's default, which is what the transfer would have used.
     */
    public static RepairDestination destinationOf(Repair repair) {
        if (repair.getRepairDestination() != null) return repair.getRepairDestination();
        AssetType category = repair.getAsset() != null ? repair.getAsset().getAssetType() : null;
        return category != null && category.getRepairDestination() != null
                ? category.getRepairDestination() : RepairDestination.IT;
    }

    private static String unitRoleFor(RepairDestination routing) {
        if (routing == null) return pride.bank.erp.helper.Constants.INFRA_UNIT_ROLE;
        return switch (routing) {
            case IT -> pride.bank.erp.helper.Constants.INFRA_UNIT_ROLE;
            case ADMIN -> pride.bank.erp.helper.Constants.ADMIN_UNIT_ROLE;
            case EXTERNAL -> null;
        };
    }

    private static String personName(pride.bank.erp.user.User user) {
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }


    @Transactional
    /**
     * One asset's repair history.
     *
     * <p>Scoped to the asset, not just to {@code READ_ASSET}. A repair record says what failed, who
     * held it and where it went — the same exposure as the asset itself — so guarding the asset's
     * detail page while leaving this open would let a branch officer read another branch's
     * maintenance trail by calling this with the id they were refused a moment earlier.
     */
    public Page<RepairDTO> listRepairDetails(Long assetId, Pageable pageable)
            throws pride.bank.erp.exception.NotFoundException {
        assetService.requireAssetReadable(assetId);
        return repairRepository.findByAssetId(assetId, pageable)
                .map(this::convertToDTO);
    }

    /**
     * Repairs across every asset, for the maintenance report. Each filter is optional.
     *
     * <p>Branch-scoped from the caller's permissions rather than from the request, so the report
     * answers "what has been repaired that I may see" rather than "what has been repaired". Null
     * from the resolver means ALL scope and no restriction.
     */
    public Page<RepairDTO> listAllRepairs(
            String status, Long assetTypeId,
            java.time.LocalDateTime from, java.time.LocalDateTime to,
            Pageable pageable) {
        Long scopedBranchId = accessScopeService.resolveBranch(
                pride.bank.erp.access.ScopeSubject.ASSETS, null);
        return repairRepository.findForReport(status, assetTypeId, from, to, scopedBranchId, pageable)
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
                // Carried so the maintenance report can say where a repaired asset belongs; without
                // it every row read "—" under Branch.
                .branch(asset.getBranch())
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
                .technicianType(repair.getTechnicianType())
                .technicianUserId(repair.getTechnicianUser() != null ? repair.getTechnicianUser().getId() : null)
                .technicianConsultantId(repair.getTechnicianConsultant() != null
                        ? repair.getTechnicianConsultant().getId() : null)
                .repairDestination(destinationOf(repair))
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

    /**
     * Closes a repair on the bench, without the asset travelling anywhere yet.
     *
     * <p>Kept alongside {@link #closeForReturn} because the two answer different questions: this one
     * says <em>the work is finished</em>, the other says <em>it is finished and going home</em>. A
     * bench that clears its queue on a Friday and ships on a Monday needs the first.
     *
     * <h2>Two faults fixed here</h2>
     * <b>The status id was hardcoded and wrong.</b> It read {@code 12L} under a comment saying
     * "return the asset to store" - measured, id 12 is {@code stockCompleted}, a <em>stock-order</em>
     * status, applied to an asset. Its sibling on the old create path used {@code 13L} as "the repair
     * status"; id 13 is {@code sentToStore}, and the real {@code inMaintenance} is id 7. So both ends
     * of that flow stamped a status nobody meant. This is trap #4 in CLAUDE.md, and it is why ids are
     * resolved by code here.
     *
     * <p><b>Nothing asked whose asset it was.</b> The endpoint checked {@code REPAIR_ASSET} and the
     * service reached the asset by id with no scope check, so a holder at one branch could close a
     * repair on any asset in the bank - the same gap {@code requireAssetInScope} closed for the
     * movement-side repair actions.
     */
    @Transactional
    public Repair completeRepair(
            Long repairId,
            LocalDateTime repairEndDate,
            String completionNotes,
            MultipartFile[] files,
            TechnicianSelectionDTO technician
    ) throws NotFoundException, IOException {
        Repair repair = findOneRepair(repairId);
        Asset asset = repair.getAsset();
        requireAssetInScope(asset);
        // Before anything is written: a refusal here must not leave the asset's status changed.
        requireTechnicianForCompletion(repair, technician);

        // A repaired asset is sitting on the bench it was repaired on, which is a store. Resolved by
        // code: the literal that used to stand here named a stock-order status.
        Status inStore = statusService.findByStatusCode(CODE_SENT_TO_STORE);
        asset.setAssetStatus(inStore);
        assetRepository.save(asset);

        applyCompletion(repair, repairEndDate, completionNotes, uploadAll(files));
        return repairRepository.save(repair);
    }

    /**
     * The one place a repair is marked finished, so the two ways in cannot disagree about what
     * "completed" means - which is how the bank came to have two repair flows in the first place.
     */
    private void applyCompletion(Repair repair, LocalDateTime endDate, String notes,
                                 List<String> documents) {
        repair.setRepairEndDate(endDate == null ? LocalDateTime.now() : endDate);
        repair.setCompletionNotes(notes == null || notes.isBlank() ? null : notes.trim());
        repair.setCompletionDocuments(documents == null ? new ArrayList<>() : new ArrayList<>(documents));
        repair.setStatus(STATUS_COMPLETED);

        Asset asset = repair.getAsset();
        if (asset != null) {
            audit(pride.bank.erp.auditing.trail.AuditEventType.UPDATED, "REPAIR_COMPLETED", asset,
                    "Completed repair on " + describe(asset)
                            + (repair.getCompletionNotes() == null
                                    ? "" : " — " + repair.getCompletionNotes()));
        }
    }

    private List<String> uploadAll(MultipartFile[] files) throws IOException {
        List<String> paths = new ArrayList<>();
        if (files == null) return paths;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                paths.add(fileUpload.uploadFileToLocalDirectory(file));
            }
        }
        return paths;
    }

    /**
     * Refuses unless this asset is within the caller's reach.
     *
     * <p>Mirrors {@code RepairMovementService#requireAssetInScope} - MANAGE, on the asset's branch
     * and holder. Closing a repair changes the asset's status, which is a change to the record.
     */
    private void requireAssetInScope(Asset asset) {
        if (asset == null) return;
        Long branchId = asset.getBranch() != null ? asset.getBranch().getId() : null;
        Long holderId = asset.getAssignedTo() != null ? asset.getAssignedTo().getId() : null;
        accessScopeService.requireCanManage(
                pride.bank.erp.access.ScopeSubject.ASSETS, branchId, holderId);
    }
}