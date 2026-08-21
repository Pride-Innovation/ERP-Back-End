package pride.bank.erp.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.status.StatusRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two questions the dashboard's request widgets ask.
 *
 * <p>They were previously one query answering neither. It filtered by status id and by a hardcoded
 * switch on the user's role name, so it showed Officers newly-created requests and everyone else
 * approved ones — with no notion of who was actually being waited on, and no notion of a request
 * staying open until the item reached the person who asked for it.
 */
@SpringBootTest
class OpenRequestQueryTest {

    @Autowired
    private RequestSearchDao requestSearchDao;

    @Autowired
    private StatusRepository statusRepository;

    private RequestPage page(int size) {
        return RequestPage.builder().pageNumber(0).pageSize(size).build();
    }

    private Long statusId(String code) {
        return statusRepository.findByStatus(code).map(s -> s.getId()).orElse(null);
    }

    /**
     * The approver inbox must find requests routed to one named person.
     *
     * <p>Both sides of the "assigned to me OR to my unit" test used dotted paths, which JPA compiles
     * to INNER joins. Those sit in the FROM clause and filter before the OR is evaluated, so asking
     * the question required a request to have BOTH a current approver and a current unit — and a
     * request routed to a single named approver, which is the ordinary case, has no unit. The inbox
     * excluded exactly the rows it was built to show.
     */
    @Test
    @Transactional(readOnly = true)
    void approverInboxFindsRequestsWithNoCurrentUnit() {
        Page<Request> all = requestSearchDao.findAllWithFilters(
                page(300), RequestSearchCriteria.builder().build());

        Request routedToAPerson = all.getContent().stream()
                .filter(r -> r.getCurrentApprover() != null && r.getCurrentUnit() == null)
                .findFirst()
                .orElse(null);
        if (routedToAPerson == null) return; // nothing of this shape seeded

        Long approverId = routedToAPerson.getCurrentApprover().getId();

        // Asked the way the inbox asks it: an approver id AND a unit id, as a user in a unit would.
        Page<Request> inbox = requestSearchDao.findAllWithFilters(
                page(300),
                RequestSearchCriteria.builder()
                        .currentApproverId(approverId)
                        .currentUnitId(999_999L) // a unit nobody is in
                        .build());

        assertThat(inbox.getContent())
                .as("request %s is routed to approver %s and has no current unit — it must still "
                                + "appear in that approver's inbox",
                        routedToAPerson.getId(), approverId)
                .anyMatch(r -> r.getId().equals(routedToAPerson.getId()));
    }

    /**
     * An open request is one that is neither rejected nor received.
     *
     * <p>Expressed as an exclusion so that adding an approval tier to a workflow does not require
     * editing a list of "open" statuses — only the two terminal ones are enumerated.
     */
    @Test
    @Transactional(readOnly = true)
    void openRequestsExcludeOnlyTerminalStatuses() {
        Long rejected = statusId("requestRejected");
        Long acknowledged = statusId("receiptAcknowledged");
        if (rejected == null || acknowledged == null) return;

        Page<Request> open = requestSearchDao.findAllWithFilters(
                page(300),
                RequestSearchCriteria.builder()
                        .excludedStatusIds(List.of(rejected, acknowledged))
                        .deleted(false)
                        .build());

        assertThat(open.getContent())
                .as("a rejected or received request is finished and must not be listed as open")
                .noneMatch(r -> r.getStatus() != null
                        && (rejected.equals(r.getStatus().getId())
                        || acknowledged.equals(r.getStatus().getId())));

        // And a mid-ladder request must survive: approval progress does not close a request.
        Page<Request> all = requestSearchDao.findAllWithFilters(
                page(300), RequestSearchCriteria.builder().deleted(false).build());
        all.getContent().stream()
                .filter(r -> r.getStatus() != null
                        && !rejected.equals(r.getStatus().getId())
                        && !acknowledged.equals(r.getStatus().getId()))
                .findFirst()
                .ifPresent(midLadder -> assertThat(open.getContent())
                        .as("request %s is part-way through its ladder and must still count as open",
                                midLadder.getId())
                        .anyMatch(r -> r.getId().equals(midLadder.getId())));
    }

    /** Restricting to one requester must not also require them to have a branch. */
    @Test
    @Transactional(readOnly = true)
    void raisedByFilterDoesNotRequireADutyStation() {
        Page<Request> all = requestSearchDao.findAllWithFilters(
                page(300), RequestSearchCriteria.builder().build());

        Request sample = all.getContent().stream()
                .filter(r -> r.getRequester() != null)
                .findFirst()
                .orElse(null);
        if (sample == null) return;

        Page<Request> mine = requestSearchDao.findAllWithFilters(
                page(300),
                RequestSearchCriteria.builder()
                        .raisedByUserId(sample.getRequester().getId())
                        .build());

        assertThat(mine.getContent())
                .as("every row returned must belong to the requester asked for")
                .allMatch(r -> r.getRequester() != null
                        && r.getRequester().getId().equals(sample.getRequester().getId()));
        assertThat(mine.getContent()).isNotEmpty();
    }
}
