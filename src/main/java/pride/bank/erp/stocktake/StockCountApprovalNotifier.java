/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.stocktake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pride.bank.erp.appnotification.AppNotificationService;
import pride.bank.erp.appnotification.NotificationTarget;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.notification.Email;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Tells the approvers at a branch that a stock count is waiting for them, in the app and by email.
 *
 * <h2>Who is told</h2>
 * Everyone who could approve it: an enabled, unblocked account holding APPROVE_STOCK_TAKE whose duty
 * station is the branch the store stands at - the same "at your own branch" rule
 * {@code StockTakeService.canActAt} applies - other than the person who opened the count, who may not
 * approve it, and the person submitting, who knows already.
 *
 * <p>Super administrators elsewhere are deliberately not told. They may act anywhere, so naming them
 * would put every branch's count in their inbox, and the decision belongs at the branch.
 *
 * <h2>Sent after the submission commits</h2>
 * Not inside it, for two reasons. A submission that then fails must not have told anyone it happened,
 * and a failing notification must not undo a submission: {@code createAndPush} is transactional, and
 * an exception crossing it would mark the submission's transaction for rollback even if caught here.
 * The recipients and the wording are read while the session is open; only plain values and the users
 * cross into the after-commit callback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockCountApprovalNotifier {

    static final String TYPE = "STOCK_COUNT_PENDING";

    private final UserRepository userRepository;
    private final AppNotificationService notificationService;
    private final Email email;

    @Value("${front-end.environment}")
    private String frontEndUrl;

    /** Everyone who may approve this count. The opener is never among them. */
    public List<User> approversFor(StockCount count) {
        Long branchId = count.getStore() != null && count.getStore().getLocation() != null
                ? count.getStore().getLocation().getId() : null;
        if (branchId == null) return List.of();
        Long openerId = count.getCountedBy() != null ? count.getCountedBy().getId() : null;

        return userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && branchId.equals(u.getBranch().getId()))
                .filter(u -> !u.getId().equals(openerId))
                .filter(u -> u.isEnabled() && !u.isBlocked())
                .filter(u -> u.getAuthorities().stream()
                        .anyMatch(a -> Constants.APPROVE_STOCK_TAKE.equals(a.getAuthority())))
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    /** Called as a count is submitted. Never throws: a notice that fails must not fail the submission. */
    public void onSubmitted(StockCount count, CountViewSummary summary, User submitter) {
        try {
            List<User> recipients = approversFor(count).stream()
                    .filter(u -> submitter == null || !u.getId().equals(submitter.getId()))
                    .toList();
            if (recipients.isEmpty()) {
                log.warn("Stock count {} was submitted and no other approver at its branch was found to tell",
                        count.getReference());
                return;
            }
            Notice notice = new Notice(count.getId(), count.getReference(),
                    count.getStore() != null ? count.getStore().getName() : null,
                    count.getStore() != null && count.getStore().getLocation() != null
                            ? count.getStore().getLocation().getName() : null,
                    fullName(submitter), summary.lines(), summary.linesWithVariance(),
                    recipients.stream().map(u -> new Recipient(u, u.getEmail(), fullName(u))).toList());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deliver(notice);
                    }
                });
            } else {
                deliver(notice);
            }
        } catch (Exception e) {
            log.warn("Could not prepare approval notices for stock count {}: {}", count.getReference(), e.getMessage());
        }
    }

    /** Writes one in-app notification and sends one email per recipient. Each is independent. */
    void deliver(Notice notice) {
        String message = String.format("Stock count %s for %s was submitted%s and is waiting for your approval.",
                notice.reference(), notice.storeName() != null ? notice.storeName() : "a store",
                notice.submittedBy() != null ? " by " + notice.submittedBy() : "");
        for (Recipient r : notice.recipients()) {
            try {
                notificationService.createAndPush(r.user(), "Stock Count Approval Needed", message,
                        TYPE, NotificationTarget.STOCK_COUNT, notice.countId());
            } catch (Exception e) {
                log.warn("Could not notify user {} of stock count {}: {}", r.user().getId(), notice.reference(), e.getMessage());
            }
            email.sendStockCountApprovalEmail(r.email(), r.name(), notice.reference(), notice.storeName(),
                    notice.branchName(), notice.submittedBy(), notice.lines(), notice.linesWithVariance(),
                    frontEndUrl + "/assets-mgt/store/stock-take/" + notice.countId());
        }
    }

    private static String fullName(User u) {
        if (u == null) return null;
        String name = ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                + (u.getLastName() != null ? u.getLastName() : "")).trim();
        return name.isEmpty() ? null : name;
    }

    /** The two figures the message quotes, taken from the view the submission already built. */
    public record CountViewSummary(int lines, int linesWithVariance) {}

    record Recipient(User user, String email, String name) {}

    record Notice(Long countId, String reference, String storeName, String branchName, String submittedBy,
                  int lines, int linesWithVariance, List<Recipient> recipients) {}
}
