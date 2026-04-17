package pride.bank.erp.user;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;


@Getter
@Setter
public class UserQueryParams {
    private int pageSize = 10;
    private int pageNumber = 0;
    private String sortBy = "id";
    private String sortDirection = ASCENDING_SORT_DIRECTION;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private Boolean blocked;
    private Boolean isEnabled;
    private Boolean isAccountNonLocked;
    private String name;
    private String staffNumber;
    private String title;

    /** ISO-8601 lower bound for the user's createDate (e.g. 2024-01-01T00:00:00Z) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtFrom;

    /** ISO-8601 upper bound for the user's createDate (e.g. 2024-12-31T23:59:59Z) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAtTo;
}
