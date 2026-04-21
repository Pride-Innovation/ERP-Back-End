package pride.bank.erp.department;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "head_of_dept_id")
    private User headOfDepartment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @OneToMany(mappedBy = "department", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @NotAudited
    private List<User> users;
}
