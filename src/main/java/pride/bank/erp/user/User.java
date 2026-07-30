/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;
import pride.bank.erp.token.Token;
import pride.bank.erp.unit.Unit;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(columnNames = "email")})
@EntityListeners(AuditingEntityListener.class)
@Audited
// Hibernate hands ManyToOne associations back as ByteBuddy proxies that carry an internal
// hibernateLazyInitializer/handler field. Jackson has no serializer for that, so the moment a
// proxy User gets written (e.g. RequestDTO.currentApprover from /requests) the response 500s.
// Telling Jackson to skip those two fields makes the entity safe to serialize whether it's a
// real instance or a proxy.
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    private String otherName;

    @Column(unique = true)
    private String email;

    /**
     * WRITE_ONLY rather than @JsonIgnore: the hash must never be serialised out, but Jackson must
     * still be able to bind a password in should anything deserialise into this type.
     *
     * <p>Without this, every endpoint that embeds a User — {@code /requests} was returning 77KB for
     * five rows — shipped each party's bcrypt hash to the client. Endpoints that map to their own
     * DTO (users, movements) were never affected; this closes it for the ones that don't.
     */
    @NotNull(message = Constants.NULL_FIELD_VALIDATOR)
    @NotBlank(message = Constants.BLANK_FIELD_VALIDATOR)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String staffNumber;
    private String gender;
    private String availability;
    private String profileImage;
    private boolean blocked;
    private boolean isActing;
    @Builder.Default
    private int failedLoginAttempts = 0;

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
    @Builder.Default
    private boolean isEnabled = false;

    @NotAudited
    @Builder.Default
    private boolean isAccountNonLocked = true;

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
    @JoinColumn(name = "acting_title_id")
    @NotAudited
//    @JsonIgnore
    private Title actingTitle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    // @OnDelete(action = OnDeleteAction.CASCADE)
    @NotAudited
    private Department department;

    /**
     * Optional unit the user belongs to (Head Office). Drives in-app visibility of
     * group-email workflow steps that target the unit's mailbox.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id")
    @NotAudited
    private Unit unit;

    /**
     * Optional secondary roles in addition to the role implied by {@link #title}.
     * Used for workflow matching (e.g. a user can be Officer-by-title and also have
     * an "Approver" role for cross-cutting approvals). Empty for most users.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @NotAudited
    @Builder.Default
    private Set<Role> additionalRoles = new HashSet<>();

    /**
     * All role IDs that should be considered when matching this user against
     * workflow {@code requesterRoleIds} or step {@code skipIfRequesterRoleIds}.
     * Combines the role implied by {@link #title} with {@link #additionalRoles}.
     */
    @JsonIgnore
    public Set<Long> getEffectiveRoleIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (title != null && title.getRole() != null && title.getRole().getId() != null) {
            ids.add(title.getRole().getId());
        }
        if (additionalRoles != null) {
            for (Role r : additionalRoles) {
                if (r != null && r.getId() != null) {
                    ids.add(r.getId());
                }
            }
        }
        return ids;
    }

    /**
     * Spring Security reads this directly; it is not part of the API contract. Clients derive
     * permissions from {@code title.role.permissions} + {@code additionalRoles}, so serialising the
     * expanded authority list only duplicated authorisation data onto the wire.
     */
    @JsonIgnore
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<String> permissionNames = new LinkedHashSet<>();
        if (title != null && title.getRole() != null && title.getRole().getPermissions() != null) {
            title.getRole().getPermissions().forEach(p -> {
                if (p != null && p.getName() != null) permissionNames.add(p.getName());
            });
        }
        if (additionalRoles != null) {
            for (Role role : additionalRoles) {
                if (role != null && role.getPermissions() != null) {
                    role.getPermissions().forEach(p -> {
                        if (p != null && p.getName() != null) permissionNames.add(p.getName());
                    });
                }
            }
        }
        return permissionNames.stream()
                .map(SimpleGrantedAuthority::new)
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
        return isAccountNonLocked;
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