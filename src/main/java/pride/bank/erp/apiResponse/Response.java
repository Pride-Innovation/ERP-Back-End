package pride.bank.erp.apiResponse;

import lombok.*;
import org.springframework.stereotype.Component;

@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response {
    private String status;
    private String message;
}
