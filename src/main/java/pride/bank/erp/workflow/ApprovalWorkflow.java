package pride.bank.erp.workflow;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.role.Role;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Asset categories this workflow applies to.
     * <p>Empty set = applies to all asset categories.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "approval_workflow_asset_type",
            joinColumns = @JoinColumn(name = "workflow_id"),
            inverseJoinColumns = @JoinColumn(name = "asset_type_id")
    )
    @Builder.Default
    private Set<AssetType> assetTypes = new HashSet<>();

    /** Null = applies to all branches within the branchScope */
    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkflowBranchScope branchScope = WorkflowBranchScope.ALL;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Personal requests, store-restock requests, or both (V30). Matched against the request's purpose. */
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 16)
    @Builder.Default
    private WorkflowPurpose appliesTo = WorkflowPurpose.BOTH;

    /** Lower number = higher precedence when multiple workflows match */
    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    /**
     * Requester roles this workflow applies to.
     * <p>Empty set = applies to any requester.
     * Otherwise the requester's effective roles must intersect this set.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "approval_workflow_requester_role",
            joinColumns = @JoinColumn(name = "workflow_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> requesterRoles = new HashSet<>();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<ApprovalStep> steps = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;
}
