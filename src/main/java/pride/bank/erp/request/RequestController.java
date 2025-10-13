/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.ResourceNotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
public class RequestController {
    @Autowired
    private RequestService requestService;

    @GetMapping("/requests")
    public ResponseEntity<Page<RequestDTO>> fetchAllRequests(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDateTime timeOfSubmissionOfRequest,
            @RequestParam(required = false) List<Long> statusIds,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) String approver,
            @RequestParam(required = false, defaultValue = "false") Boolean deleted,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {
        Page<RequestDTO> requests = requestService.fetchAllRequests(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name,
                priority,
                description,
                timeOfSubmissionOfRequest,
                statusIds,
                status,
                requestedBy,
                approver,
                deleted,
                startDate,
                endDate
        );
        return new ResponseEntity<>(requests, HttpStatus.OK);
    }

    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createRequest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("priority") String priority,
            @RequestParam("description") String description,
            @RequestParam("requestCommodities") String requestCommodities
    ) {
        List<RequestCommodityDTO> commodityList;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            commodityList = objectMapper.readValue(requestCommodities,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RequestCommodityDTO.class));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error deserializing requestCommodities: " + e.getMessage());
        }

        Request request = Request.builder()
                .name(name)
                .priority(priority)
                .description(description)
                .build();

        try {
            RequestDTO savedRequest = requestService.createRequest(request, file, commodityList);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
        } catch (BadRequestException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File processing failed.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Request creation failed due to an unexpected error.");
        }
    }

    @PutMapping(value = "/requests/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateRequest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("priority") String priority,
            @RequestParam("description") String description,
            @RequestParam("requestCommodities") String requestCommodities,
            @PathVariable Long id
    ) {
        List<RequestCommodityDTO> commodityList;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            commodityList = objectMapper.readValue(requestCommodities,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RequestCommodityDTO.class));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error deserializing requestCommodities: " + e.getMessage());
        }

        Request request = Request.builder()
                .name(name)
                .priority(priority)
                .description(description)
                .build();
        try {
            RequestDTO updatedRequest = requestService.updateRequest(id, request, file, commodityList);
            return ResponseEntity.ok(updatedRequest);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Request update failed due to an unexpected error.");
        }
    }

    @DeleteMapping("/requests/{requestID}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long requestID) {
        try {
            var response = requestService.deleteRequest(requestID);
            return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/requests/{requestID}")
    public ResponseEntity<?> findRequest(@PathVariable Long requestID) {
        try {
            RequestDTO response = requestService.fetchSingleRequest(requestID);
            return new ResponseEntity<>(response, HttpStatusCode.valueOf(200));
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/current-year-summary")
    public ResponseEntity<List<AssetRequestSummaryDTO>> getCurrentYearRequestSummary() {
        return ResponseEntity.ok(requestService.getCurrentYearRequestSummary());
    }


    @GetMapping("/latest-pending-request")
    public ResponseEntity<Page<RequestDTO>> findLatestPendingRequestsWithDetails(
            @RequestParam(required = false, defaultValue = "3") int pageSize,
            @RequestParam(required = false, defaultValue = DESCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) LocalDateTime timeOfSubmissionOfRequest,
            @RequestParam(required = false, defaultValue = "3") List<Long> statusIds, // Status Ids for an Approved Request.
            @RequestParam(required = false, defaultValue = "DASHBOARD") String status
    ) {
        try {

            Page<RequestDTO> response = requestService.findLatestPendingRequestsWithDetails(
                    pageSize,
                    sortDirection,
                    pageNumber,
                    sortBy,
                    name,
                    priority,
                    description,
                    timeOfSubmissionOfRequest,
                    statusIds,
                    status
            );
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception ex) {
            return null;
        }
    }
}
