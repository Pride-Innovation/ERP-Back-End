package pride.bank.erp.department;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.envers.NotAudited;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.user.User;

import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String managersGroupEmail;
    private String shortCode;

    // Excluded from Lombok toString/equals: User ↔ Department is bidirectional, and @Data
    // includes even @JsonIgnore'd fields — any toString() (e.g. Spring's debug "Writing [...]"
    // log) recursed User → department → users → User… into a StackOverflowError.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "head_of_dept_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User headOfDepartment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @OneToMany(mappedBy = "department", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @NotAudited
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<User> users;
}
