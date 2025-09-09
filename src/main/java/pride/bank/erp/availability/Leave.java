package pride.bank.erp.availability;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.NotAudited;
import pride.bank.erp.user.User;

import java.time.LocalDateTime;

@Entity(name = "leave_request")
@Table(name = "leave_request")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Leave {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String leaveType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String reason;
    private Boolean active;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "acting_user")
    @NotAudited
    private User actingUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "on_leave_user")
    @NotAudited
    private User onLeaveUser;
}
