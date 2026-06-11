package pride.bank.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatusSeeder {

    private final StatusRepository statusRepository;

    public void seedStatuses() {
        ensureStatus("Asset Request Created",  "requestCreated",  "assetrequestcreated");
        ensureStatus("Request Rejected",       "requestRejected", "requestrejected");
        ensureStatus("Request Approved",       "requestApproved", "requestapproved");
        ensureStatus("Issuance Approved",      "issuanceApproved","issuanceapproved");
        ensureStatus("Available for Issuance", "issuanceAvailable","issuanceavailable");
        ensureStatus("Issued",                 "issued",          "issued");
        ensureStatus("In Maintenance",         "inMaintenance",   "inmaintenance");
        ensureStatus("Receipt Acknowledged",   "receiptAcknowledged", "receiptacknowledged");
        ensureStatus("Asset Assigned",         "assetAssigned",   "assetassigned");
        ensureStatus("Require Update",         "requireUpdate",   "requireupdate");
        ensureStatus("Stock Pending",          "stockPending",    "stockpending");
        ensureStatus("Stock Completed",        "stockCompleted",  "stockcompleted");
        ensureStatus("Sent to Store",              "sentToStore",            "senttostore");
        ensureStatus("Manager Approved",           "managerApproved",        "managerapproved");
        ensureStatus("HOD Approved",               "hodApproved",            "hodapproved");
        ensureStatus("BOM Approved",               "bomApproved",            "bomapproved");
        ensureStatus("Branch Manager Approved",    "branchManagerApproved",  "branchmanagerapproved");
        ensureStatus("Supervisor Approved",        "supervisorApproved",     "supervisorapproved");
        ensureStatus("Unit Acknowledged",          "unitAcknowledged",       "unitacknowledged");
        log.info("Status seeding complete");
    }

    private void ensureStatus(String name, String statusCode, String code) {
        statusRepository.findByStatus(statusCode).orElseGet(() -> {
            log.info("Creating status: {}", name);
            return statusRepository.save(Status.builder()
                    .name(name)
                    .status(statusCode)
                    .code(code)
                    .description(name)
                    .build());
        });
    }
}
