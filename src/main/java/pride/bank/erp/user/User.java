/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.department.Department;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.request.Request;
import pride.bank.erp.title.Title;
import pride.bank.erp.token.Token;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(columnNames = "email")})
@EntityListeners(AuditingEntityListener.class)
@Audited
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String firstName;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Size(max = 20, min = 3, message = Constants.NAME_VALIDATOR)
    private String lastName;

    @Column(unique = true)
    @Email(message = Constants.EMAIL_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    private String email;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    private String password;

    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @Pattern(regexp = Constants.PHONE_REGEX, message = Constants.PHONE_VALIDATOR)
    private String phone;

    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    private String address;

    private String profileImage;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    // @Column(insertable = false)
    private LocalDateTime lastModified;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedBy
    // @Column(insertable = false)
    private Long lastModifiedBy;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonIgnore
    @NotAudited
    private List<Token> tokens;

    @NotAudited
    private boolean isEnabled = false;

    @OneToMany(mappedBy = "requester", cascade = CascadeType.PERSIST)
    @JsonIgnore
    @NotAudited
    private List<Request> requests;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    @NotAudited
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "title_id")
    @NotAudited
    private Title title;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    // @OnDelete(action = OnDeleteAction.CASCADE)
    @NotAudited
    private Department department;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (title == null || title.getRole() == null || title.getRole().getPermissions() == null) {
            return List.of();
        }
        return title.getRole().getPermissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }
}