package pride.bank.erp.user;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.role.Role;
import pride.bank.erp.title.Title;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String otherName;
    private String email;
    private String staffNumber;
    private String gender;
    private String availability;
    private String profileImage;
    private boolean blocked;
    /**
     * Whether this person is currently acting in another title.
     *
     * <p>Pinned for the same reason as {@code isHeadOffice} above: {@code isActing()} publishes as
     * {@code "acting"}. Nothing reads it on the front end yet, which makes this the cheapest possible
     * moment to state the name rather than leave the next consumer to discover it.
     */
    @JsonProperty("isActing")
    private boolean isActing;
    private int failedLoginAttempts;
    private boolean isEnabled;
    private boolean isAccountNonLocked;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;

    private DepartmentSummary department;
    private UnitSummary unit;
    private BranchSummary branch;
    private TitleSummary title;
    private TitleSummary actingTitle;
    private Set<RoleSummary> additionalRoles;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DepartmentSummary {
        private Long id;
        private String name;
        private String shortCode;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class UnitSummary {
        private Long id;
        private String name;
        private String groupEmail;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class BranchSummary {
        private Long id;
        private String name;
        private String shortCode;
        /**
     * Whether this branch is the Head Office.
     *
     * <h2>Why the wire name is pinned</h2>
     * Lombok gives a primitive {@code boolean isHeadOffice} the getter {@code isHeadOffice()}, and
     * Jackson strips the {@code is} prefix — so this published as {@code "headOffice"} while most
     * consumers asked for {@code isHeadOffice} and silently got {@code undefined}.
     *
     * <p>Three of the five readers were broken by it: the store page's Head Office fallback could
     * never find a branch, the "HQ" badge on a branch never rendered, and the user form's rule
     * <em>"Department is required for Head Office staff"</em> never fired. The two that worked did so
     * by checking <b>both</b> spellings, with a comment explaining the trap — which is to tell that
     * a derived name is a name every consumer has to rediscover.
     *
     * <p>Safe in both directions: nothing posts this field, and the two defensive readers accept
     * either spelling, so pinning fixes three call sites and breaks none.
     */
    @JsonProperty("isHeadOffice")
    private boolean isHeadOffice;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TitleSummary {
        private Long id;
        private String name;
        private String shortCode;
        private Long reportsToId;
        private String reportsToName;
        private Long roleId;
        private String roleName;

        public static TitleSummary from(Title title) {
            if (title == null) return null;
            return TitleSummary.builder()
                    .id(title.getId())
                    .name(title.getName())
                    .shortCode(title.getShortCode())
                    .reportsToId(title.getReportsTo() != null ? title.getReportsTo().getId() : null)
                    .reportsToName(title.getReportsTo() != null ? title.getReportsTo().getName() : null)
                    .roleId(title.getRole() != null ? title.getRole().getId() : null)
                    .roleName(title.getRole() != null ? title.getRole().getName() : null)
                    .build();
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class RoleSummary {
        private Long id;
        private String name;

        public static RoleSummary from(Role role) {
            if (role == null) return null;
            return RoleSummary.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .build();
        }
    }

    public static UserResponseDTO from(User user) {
        if (user == null) return null;
        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .otherName(user.getOtherName())
                .email(user.getEmail())
                .staffNumber(user.getStaffNumber())
                .gender(user.getGender())
                .availability(user.getAvailability())
                .profileImage(user.getProfileImage())
                .blocked(user.isBlocked())
                .isActing(user.isActing())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .isEnabled(user.isEnabled())
                .isAccountNonLocked(user.isAccountNonLocked())
                .createDate(user.getCreateDate())
                .lastModified(user.getLastModified())
                .createdBy(user.getCreatedBy())
                .lastModifiedBy(user.getLastModifiedBy())
                .department(user.getDepartment() == null ? null : DepartmentSummary.builder()
                        .id(user.getDepartment().getId())
                        .name(user.getDepartment().getName())
                        .shortCode(user.getDepartment().getShortCode())
                        .build())
                .unit(user.getUnit() == null ? null : UnitSummary.builder()
                        .id(user.getUnit().getId())
                        .name(user.getUnit().getName())
                        .groupEmail(user.getUnit().getGroupEmail())
                        .build())
                .branch(user.getBranch() == null ? null : BranchSummary.builder()
                        .id(user.getBranch().getId())
                        .name(user.getBranch().getName())
                        .shortCode(user.getBranch().getShortCode())
                        .isHeadOffice(user.getBranch().isHeadOffice())
                        .build())
                .title(TitleSummary.from(user.getTitle()))
                .actingTitle(TitleSummary.from(user.getActingTitle()))
                .additionalRoles(user.getAdditionalRoles() == null ? null :
                        user.getAdditionalRoles().stream()
                                .map(RoleSummary::from)
                                .collect(Collectors.toSet()))
                .build();
    }
}
