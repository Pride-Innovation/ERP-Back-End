package pride.bank.erp.issuanceApprovalRecord;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import pride.bank.erp.issuance.Issuance;
import pride.bank.erp.request.Request;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Audited
public class IssuanceApprovalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String comment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "issuance_id")
    @NotAudited
    private Issuance issuance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id")
    @NotAudited
    private Request request;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @NotAudited
    private User user;

    @CreatedDate
    private LocalDateTime createDate;

    @LastModifiedDate
    private LocalDateTime lastModified;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long lastModifiedBy;
}
