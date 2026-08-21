/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.BusinessRuleException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.exception.ResourceNotFoundException;
import pride.bank.erp.exception.ValidationException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.requestCommodity.RequestCommodityDTO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Slf4j
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
            LocalDateTime endDate,

            @RequestParam(required = false) Long currentApproverId
    ) {
        System.out.println("Request information!!!");
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
                endDate,
                currentApproverId
        );
        return new ResponseEntity<>(requests, HttpStatus.OK);
    }

    @PostMapping(value = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createRequest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("priority") String priority,
            @RequestParam("description") String description,
            @RequestParam("requestCommodities") String requestCommodities,
            @RequestParam(value = "assetTypeId", required = false) String assetTypeIdRaw,
            @RequestParam(value = "attributes", required = false) String attributesRaw
    ) {
        List<RequestCommodityDTO> commodityList;
        Long assetTypeId;
        Map<String, Object> attributes;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            commodityList = objectMapper.readValue(requestCommodities,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RequestCommodityDTO.class));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error deserializing requestCommodities: " + e.getMessage());
        }

        try {
            assetTypeId = parseAssetTypeId(assetTypeIdRaw);
            attributes = parseAttributes(attributesRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        Request request = Request.builder()
                .name(name)
                .priority(priority)
                .description(description)
                .build();

        try {
            RequestDTO savedRequest = requestService.createRequest(request, file, commodityList, assetTypeId, attributes);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
        } catch (ValidationException | BusinessRuleException | BadRequestException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (NotFoundException | ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File processing failed.");
        } catch (Exception e) {
            log.error("Unexpected error creating request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Request creation failed: " + e.getMessage());
        }
    }

    @PutMapping(value = "/requests/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateRequest(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("priority") String priority,
            @RequestParam("description") String description,
            @RequestParam("requestCommodities") String requestCommodities,
            @RequestParam(value = "assetTypeId", required = false) String assetTypeIdRaw,
            @RequestParam(value = "attributes", required = false) String attributesRaw,
            @PathVariable Long id
    ) {
        List<RequestCommodityDTO> commodityList;
        Long assetTypeId;
        Map<String, Object> attributes;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            commodityList = objectMapper.readValue(requestCommodities,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RequestCommodityDTO.class));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Error deserializing requestCommodities: " + e.getMessage());
        }

        try {
            assetTypeId = parseAssetTypeId(assetTypeIdRaw);
            attributes = parseAttributes(attributesRaw);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        Request request = Request.builder()
                .name(name)
                .priority(priority)
                .description(description)
                .build();
        try {
            RequestDTO updatedRequest = requestService.updateRequest(id, request, file, commodityList, assetTypeId, attributes);
            return ResponseEntity.ok(updatedRequest);
        } catch (ValidationException | BusinessRuleException | BadRequestException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (NotFoundException | ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File processing failed.");
        } catch (Exception e) {
            log.error("Unexpected error updating request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Request update failed: " + e.getMessage());
        }
    }

    private Long parseAssetTypeId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("assetTypeId must be a number");
        }
    }

    private Map<String, Object> parseAttributes(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deserializing attributes: " + e.getMessage());
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

    /**
     * Requested vs delivered per category, current year, scoped to the viewer.
     *
     * @param branchId the branch the dashboard's selector is focused on; only meaningful at ALL
     *                 scope, and naming another branch without it is refused rather than rewritten.
     */
    @GetMapping("/current-year-summary")
    public ResponseEntity<List<AssetRequestSummaryDTO>> getCurrentYearRequestSummary(
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(requestService.getCurrentYearRequestSummary(branchId));
    }


    /**
     * Requests waiting on the signed-in user's own decision.
     *
     * <p>Assigned to them individually, or to a unit they belong to for a group step. A request
     * leaves this list the moment they act on it, because approving moves the workflow's current
     * approver to the next tier.
     *
     * <p>Deliberately takes no scope or branch parameter: "who is being waited on" is a fact about
     * the workflow, not a question of breadth. Widening it by branch would show an approver work
     * that is not theirs to do.
     */
    @GetMapping("/requests/awaiting-me")
    public ResponseEntity<Page<RequestDTO>> findAwaitingMyDecision(
            @RequestParam(required = false, defaultValue = "25") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber) {
        return ResponseEntity.ok(requestService.findAwaitingMyDecision(pageSize, pageNumber));
    }

    /**
     * Open requests — raised and not yet finished — within the viewer's dashboard scope.
     *
     * <p>A request stays open through every approval tier, through issuance and through the movement
     * carrying the item, and drops off only when the requester acknowledges receipt.
     */
    @GetMapping("/requests/open")
    public ResponseEntity<Page<RequestDTO>> findOpenRequests(
            @RequestParam(required = false, defaultValue = "25") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(requestService.findOpenRequests(pageSize, pageNumber, branchId));
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
