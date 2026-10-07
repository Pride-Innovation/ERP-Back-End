package pride.bank.erp.repair;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.asset.Asset;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Repair {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private LocalDateTime repairStartDate;
    private LocalDateTime repairEndDate;

    /**
     * The technician's name as it was when recorded. Kept beside the references below so a past
     * repair still reads correctly after a rename, and so rows from before V26 - typed text with no
     * reference - display unchanged.
     */
    private String technician;

    /** Null until somebody is recorded; then which of the two references below is set. */
    @Enumerated(EnumType.STRING)
    private TechnicianType technicianType;

    /** Set when {@link #technicianType} is INTERNAL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_user_id")
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private pride.bank.erp.user.User technicianUser;

    /** Set when {@link #technicianType} is EXTERNAL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_consultant_id")
    @JsonIgnore
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private pride.bank.erp.consultant.Consultant technicianConsultant;

    /**
     * Where the repair went - which decides whose staff may be recorded as its internal technician.
     * Null on records opened before V26 with no transfer to read it from; see
     * {@code RepairService#destinationOf}.
     */
    @Enumerated(EnumType.STRING)
    private pride.bank.erp.assetType.RepairDestination repairDestination;
    private String repairReason;
    private String status;
    private String completionNotes;

    @ElementCollection
    @CollectionTable(name = "repair_documents", joinColumns = @JoinColumn(name = "repair_id"))
    @Column(name = "document")
    private List<String> documents;

    @ElementCollection
    @CollectionTable(name = "repair_completion_documents", joinColumns = @JoinColumn(name = "repair_id"))
    @Column(name = "document")
    private List<String> completionDocuments;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id")
    @JsonIgnore
    private Asset asset;
}
