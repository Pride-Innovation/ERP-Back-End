package pride.bank.erp.status;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;


@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping("/statuses")
    public ResponseEntity<Page<Status>> fetchAllStatuses(Pageable pageable) {
        Page<Status> response = statusService.fetchAllStatuses(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/statuses")
    public ResponseEntity<Status> createStatus(@RequestBody Status status) {
        Status response = statusService.createStatus(status);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/statuses/{id}")
    public ResponseEntity<Status> updateStatus(
            @PathVariable Long id,
            @RequestBody Status status
    ) throws NotFoundException {
        Status response = statusService.updateStatus(status, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @DeleteMapping("/statuses/{id}")
    public ResponseEntity<DeleteResponse> deleteStatus(@PathVariable Long id) {
        DeleteResponse response = statusService.deleteStatus(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }
}
