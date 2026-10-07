package pride.bank.erp.repair;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetType.RepairDestination;
import pride.bank.erp.consultant.Consultant;
import pride.bank.erp.consultant.ConsultantRepository;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.user.StaffOptionDTO;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Who repaired it: an internal officer from the unit that services the routing, or a consultant.
 *
 * <h2>What was wrong</h2>
 * {@code Repair.technician} was a free-text box, so the record was tied to nobody. The same officer
 * could appear under several spellings, a vendor could be typed onto an in-house job, and no report
 * could ever answer what a given person or vendor had repaired. It was also never asked for again
 * after the transfer, so a closed repair routinely named nobody.
 *
 * <p>These pin the rules that replaced it: the kind first, one reference of that kind, internal
 * officers drawn from the Infra unit for IT repairs and the Admin unit for Admin repairs, an external
 * repair worked on by the vendor it went to, and a technician required by the time a repair closes.
 */
@SpringBootTest
@Transactional
class RepairTechnicianTest {

    @Autowired RepairService repairService;
    @Autowired AssetRepository assetRepository;
    @Autowired UserRepository userRepository;
    @Autowired ConsultantRepository consultantRepository;

    // ── Who is offered ───────────────────────────────────────────────────────

    /**
     * The internal list follows the routing, and is drawn by the unit's role - never its name.
     *
     * <p>Every person offered for an IT repair must belong to a unit carrying {@code INFRA_UNIT}, and
     * for an Admin repair to one carrying {@code ADMIN_UNIT}. An external repair offers nobody: it is
     * worked on by the vendor it went to.
     */
    @Test
    void internalTechniciansComeFromTheUnitServicingTheRouting() {
        assertUnitRole(repairService.listInternalTechnicians(RepairDestination.IT), Constants.INFRA_UNIT_ROLE);
        assertUnitRole(repairService.listInternalTechnicians(RepairDestination.ADMIN), Constants.ADMIN_UNIT_ROLE);

        assertThat(repairService.listInternalTechnicians(RepairDestination.EXTERNAL))
                .as("an external repair has no internal technician")
                .isEmpty();
    }

    /**
     * Not branch-scoped, unlike the staff picker. Both units sit at Head Office and service the
     * whole bank; this asserts the list is the full membership, not a branch's slice of it.
     */
    @Test
    void theListIsTheWholeUnitWhereverTheCallerSits() {
        List<Long> offered = repairService.listInternalTechnicians(RepairDestination.IT).stream()
                .map(StaffOptionDTO::id).toList();
        List<Long> members = userRepository.findActiveMembersOfUnitsWithRole(Constants.INFRA_UNIT_ROLE)
                .stream().map(User::getId).toList();

        assertThat(offered).containsExactlyElementsOf(members);
    }

    // ── What is accepted ─────────────────────────────────────────────────────

    @Test
    void anInternalTechnicianOutsideTheServicingUnitIsRefused() throws Exception {
        Set<Long> infra = userRepository.findActiveMembersOfUnitsWithRole(Constants.INFRA_UNIT_ROLE)
                .stream().map(User::getId).collect(Collectors.toSet());
        User outsider = userRepository.findAll().stream()
                .filter(u -> !infra.contains(u.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("every account is in the Infra unit"));

        Repair repair = open(RepairDestination.IT);

        assertThatThrownBy(() -> repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.INTERNAL)
                .technicianUserId(outsider.getId())
                .build()))
                .as("a technician on an IT repair must be somebody from the Infra unit")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Infra");
    }

    @Test
    void anInfraOfficerIsAcceptedOnAnItRepair() throws Exception {
        List<User> infra = userRepository.findActiveMembersOfUnitsWithRole(Constants.INFRA_UNIT_ROLE);
        assumeFalse(infra.isEmpty(), "no active Infra unit members in this database");

        Repair repair = open(RepairDestination.IT);
        repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.INTERNAL)
                .technicianUserId(infra.get(0).getId())
                .build());

        assertThat(repair.getTechnicianType()).isEqualTo(TechnicianType.INTERNAL);
        assertThat(repair.getTechnicianUser().getId()).isEqualTo(infra.get(0).getId());
        assertThat(repair.getTechnicianConsultant()).isNull();
        assertThat(repair.getTechnician())
                .as("the name is kept beside the reference, so a later rename does not rewrite history")
                .isNotBlank();
    }

    /** Half a technician is not recorded: a kind with nothing chosen, or a reference of the other kind. */
    @Test
    void aKindAndItsReferenceMustAgree() throws Exception {
        Repair repair = open(RepairDestination.IT);
        Consultant vendor = vendor("Agreement Vendor", true);

        assertThatThrownBy(() -> repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.INTERNAL).build()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.INTERNAL).technicianConsultantId(vendor.getId()).build()))
                .as("a consultant id under INTERNAL")
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianConsultantId(vendor.getId()).build()))
                .as("no kind at all")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** IT can call a vendor in to its own bench, so an external technician is fine on an IT repair. */
    @Test
    void aConsultantMayWorkOnAnInHouseRepair() throws Exception {
        Repair repair = open(RepairDestination.IT);
        Consultant vendor = vendor("Bench Visitor Ltd", true);

        repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.EXTERNAL).technicianConsultantId(vendor.getId()).build());

        assertThat(repair.getTechnicianConsultant().getId()).isEqualTo(vendor.getId());
        assertThat(repair.getTechnician()).isEqualTo("Bench Visitor Ltd");
    }

    @Test
    void anInactiveConsultantCannotBeNewlyChosen() throws Exception {
        Repair repair = open(RepairDestination.IT);
        Consultant retired = vendor("Retired Vendor", false);

        assertThatThrownBy(() -> repairService.applyTechnician(repair, TechnicianSelectionDTO.builder()
                .technicianType(TechnicianType.EXTERNAL).technicianConsultantId(retired.getId()).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer");
    }

    // ── External repairs ─────────────────────────────────────────────────────

    /** The vendor an external repair goes to is its technician, recorded without being chosen twice. */
    @Test
    void anExternalRepairIsWorkedOnByItsConsultant() throws Exception {
        Consultant vendor = vendor("Routing Vendor", true);

        Repair repair = repairService.openForTransfer(anAsset(), "Hinge snapped",
                RepairDestination.EXTERNAL, null, vendor.getId(), List.of("note.pdf"));

        assertThat(repair.getTechnicianType()).isEqualTo(TechnicianType.EXTERNAL);
        assertThat(repair.getTechnicianConsultant().getId()).isEqualTo(vendor.getId());
        assertThat(repair.getRepairDestination()).isEqualTo(RepairDestination.EXTERNAL);
    }

    @Test
    void anExternalRepairRefusesAnInternalTechnician() throws Exception {
        Consultant vendor = vendor("Routing Vendor Two", true);
        User anyone = userRepository.findAll().get(0);

        assertThatThrownBy(() -> repairService.openForTransfer(anAsset(), "Hinge snapped",
                RepairDestination.EXTERNAL,
                TechnicianSelectionDTO.builder()
                        .technicianType(TechnicianType.INTERNAL).technicianUserId(anyone.getId()).build(),
                vendor.getId(), List.of("note.pdf")))
                .as("two technicians on one repair means the routing or the technician is wrong")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Completion ───────────────────────────────────────────────────────────

    /** Optional when raised; required by the time it closes. */
    @Test
    void aRepairCannotCloseWithoutSayingWhoDidIt() throws Exception {
        Asset asset = anAsset();
        repairService.openForTransfer(asset, "No display", RepairDestination.IT, null, null, List.of());

        assertThatThrownBy(() -> repairService.closeForReturn(asset, "Fixed", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who repaired");
    }

    /** One recorded earlier - at the transfer, or assigned on the bench - is enough. */
    @Test
    void aTechnicianRecordedEarlierSatisfiesCompletion() throws Exception {
        Asset asset = anAsset();
        Consultant vendor = vendor("Earlier Vendor", true);
        repairService.openForTransfer(asset, "No display", RepairDestination.IT,
                TechnicianSelectionDTO.builder()
                        .technicianType(TechnicianType.EXTERNAL).technicianConsultantId(vendor.getId()).build(),
                null, List.of());

        Repair closed = repairService.closeForReturn(asset, "Fixed", List.of(), null);

        assertThat(closed.getStatus()).isEqualTo("Completed");
        assertThat(closed.getTechnician()).isEqualTo("Earlier Vendor");
    }

    /**
     * Records from before the picker existed carry typed text and no reference. Refusing their
     * return for want of a dropdown that did not exist when they were sent would strand the asset.
     */
    @Test
    void aTypedTechnicianFromBeforeThePickerIsAccepted() throws Exception {
        Asset asset = anAsset();
        Repair legacy = repairService.openForTransfer(asset, "No display", RepairDestination.IT, null, null, List.of());
        legacy.setTechnician("HO IT Bench");

        assertThat(repairService.closeForReturn(asset, "Fixed", List.of(), null).getStatus())
                .isEqualTo("Completed");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertUnitRole(List<StaffOptionDTO> offered, String role) {
        Set<Long> members = userRepository.findAll().stream()
                .filter(u -> u.getUnit() != null
                        && u.getUnit().getRoles().stream().anyMatch(r -> role.equals(r.getName())))
                .map(User::getId)
                .collect(Collectors.toSet());
        assertThat(offered).allSatisfy(o -> assertThat(members)
                .as("%s %s offered without belonging to a unit carrying %s", o.firstName(), o.lastName(), role)
                .contains(o.id()));
    }

    private Repair open(RepairDestination routing) throws Exception {
        return repairService.openForTransfer(anAsset(), "Fault under test", routing, null, null, List.of());
    }

    private Asset anAsset() {
        return assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no asset to repair"));
    }

    private Consultant vendor(String name, boolean active) {
        return consultantRepository.save(Consultant.builder().name(name).active(active).build());
    }
}
