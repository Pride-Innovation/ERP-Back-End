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
import pride.bank.erp.advice.WafStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    private pride.bank.erp.inventory.IssueStockCheckService issueStockCheckService;

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
            /*
             * There was a `String status` parameter here and it filtered nothing.
             *
             * It was declared, accepted, passed down to the service and even written to the debug
             * log — so it read as a working filter from every angle — but `RequestSearchCriteria`
             * has no such field and `RequestSearchDao` only ever restricts on `statusIds`. The
             * reports page wired its Status dropdown to it and the dropdown did nothing at all.
             *
             * Sharper than an undeclared parameter, which Spring at least drops silently: this one
             * advertised a filter the endpoint does not implement. Removed rather than implemented,
             * because `statusIds` already does the job and a second way to say the same thing is how
             * the two drift apart. Callers still sending it are unaffected — it never did anything.
             */
            @RequestParam(required = false) String requestedBy,
            @RequestParam(required = false) String approver,
            /*
             * The same two filters, by id.
             *
             * The name forms match partially across first, last and other name — so two people called
             * Okello are one filter, and a misremembered spelling returns nothing with no hint why.
             * The page now picks a person from the directory and sends their id; the name parameters
             * stay declared so nothing that still sends one breaks.
             *
             * `approverId` is not `currentApproverId`: that one means "this is my approval inbox" and
             * suppresses the requester scope. This narrows within it.
             */
            @RequestParam(required = false) Long requesterId,
            @RequestParam(required = false) Long approverId,
            // The requester's department. Declared here as well as on the criteria because Spring
            // drops an undeclared parameter silently - a filter that looks wired and returns the
            // unfiltered list is trap #2, and this page has been bitten by it before.
            @RequestParam(required = false) Long requesterDepartmentId,
            // The requester's branch - the reports page's Branch filter, for callers who see every
            // branch. Refused below ALL unless it is the caller's own; it narrows, never widens.
            @RequestParam(required = false) Long requesterBranchId,
            @RequestParam(required = false, defaultValue = "false") Boolean deleted,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate,

            @RequestParam(required = false) Long currentApproverId
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
                requestedBy,
                approver,
                requesterId,
                approverId,
                requesterDepartmentId,
                requesterBranchId,
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
            @RequestParam(value = "attributes", required = false) String attributesRaw,
            @RequestParam(value = "forStoreRestock", required = false, defaultValue = "false") boolean forStoreRestock
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
                .forStoreRestock(forStoreRestock)
                .build();

        try {
            RequestDTO savedRequest = requestService.createRequest(request, file, commodityList, assetTypeId, attributes);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedRequest);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            // A refusal, not a fault: rethrown so it reaches the handler that answers it as one.
            // The catch-all below would turn it into a 440 "server fault".
            throw e;
        } catch (ValidationException | BusinessRuleException | BadRequestException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (NotFoundException | ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File processing failed.");
        } catch (Exception e) {
            log.error("Unexpected error creating request", e);
            return ResponseEntity.status(WafStatus.SERVER_FAULT)
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File processing failed.");
        } catch (Exception e) {
            log.error("Unexpected error updating request", e);
            return ResponseEntity.status(WafStatus.SERVER_FAULT)
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
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * A refusal is a 403, not a 400.
     *
     * The blanket catch turned every outcome into "bad request" with the raw exception text — so a
     * boundary read as a malformed call, and internals leaked into the body. AccessDeniedException
     * is left to propagate so Spring Security renders it properly.
     */
    @GetMapping("/requests/{requestID}")
    public ResponseEntity<?> findRequest(@PathVariable Long requestID) {
        try {
            RequestDTO response = requestService.fetchSingleRequest(requestID);
            return new ResponseEntity<>(response, HttpStatusCode.valueOf(200));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Whether the store this request will be issued from holds everything on it - what the request
     * page shows while it sits at its Issue step. Readable by whoever may read the request.
     */
    @GetMapping("/requests/{requestID}/stock-check")
    public ResponseEntity<?> stockCheck(@PathVariable Long requestID) {
        try {
            requestService.requireReadableById(requestID);
            return ResponseEntity.ok(issueStockCheckService.check(requestID));
        } catch (pride.bank.erp.exception.NotFoundException e) {
            // Named explicitly: the NotFoundException handler answers 200, which would read as a check.
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Requested vs delivered per category, current year, scoped to the viewer.
     *
     * @param branchId the branch the dashboard's selector is focused on; only meaningful at ALL
     *                 scope, and naming another branch without it is refused rather than rewritten.
     */
    /*
     * Outside /requests/**, so the module's route matchers never covered it and any signed-in user
     * could read it. The figures were already scoped in the service; what was missing was the gate.
     * DASH_VIEW_REQUESTS is admitted alongside READ_REQUEST because this feeds a dashboard widget,
     * and granting the dashboard axis alone is the entire point of it existing separately.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_REQUEST + "', '"
            + Constants.DASH_VIEW_REQUESTS + "')")
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

}
