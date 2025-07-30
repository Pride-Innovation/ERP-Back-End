/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetAssignmentHostory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.NotAudited;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.status.Status;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetAssignmentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NotAudited
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    @NotAudited
    private Asset asset;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "statusBefore_id")
    @NotAudited
    private Status statusBefore;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "statusAfter_id")
    @NotAudited
    private Status statusAfter;
}
