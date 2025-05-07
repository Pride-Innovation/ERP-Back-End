package pride.bank.erp.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class RequestSearchCriteria {
    private String name;
    private String priority;
    private String description;
    private LocalDateTime timeOfSubmissionOfRequest;
}
