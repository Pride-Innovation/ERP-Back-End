package pride.bank.erp.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @GetMapping("/leave")
    public ResponseEntity<List<Leave>> findAllLeave() {
        return ResponseEntity.ok(leaveService.findAllLeave());
    }

    @PostMapping("/leave/{userId}")
    public ResponseEntity<Leave> createLeave(
            @RequestBody LeaveDTO request,
            @PathVariable Long userId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                leaveService.createLeave(request, userId),
                HttpStatus.CREATED
        );
    }

}
