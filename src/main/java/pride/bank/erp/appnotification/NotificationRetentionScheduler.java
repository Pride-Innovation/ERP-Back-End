/*
13.9 Pride's Standard Copyright Notice:
Copyright (c)20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.appnotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Keeps the notification table from growing without limit.
 *
 * <h2>What it removes, and what it will not</h2>
 * Read notifications past the retention window, and nothing else. An <b>unread</b> one is outstanding
 * work — a request still waiting on somebody does not become less true for having waited three
 * months, and discarding it would take away the prompt while leaving the task. So age alone is never
 * enough.
 *
 * <p>Nothing is lost by pruning the read ones: what actually happened lives in {@code audit_event},
 * which is append-only and deliberately not this table. A notification is a nudge, not a record.
 *
 * <p>Measured when this was written: <b>760 notifications, none older than ninety days</b>. So this
 * deletes nothing today. It exists because an unbounded row-per-event log becomes the largest table
 * in the database by simply being left alone — which is how this one would have gone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetentionScheduler {

    private final AppNotificationRepository repository;

    /** Long enough that "what was I told last quarter" is still answerable from the bell. */
    private static final int RETENTION_DAYS = 90;

    /** Nightly, an hour after the leave sweep, so the two are not competing at midnight. */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void pruneReadNotifications() {
        int removed = repository.deleteReadOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (removed > 0) {
            log.info("Pruned {} read notification(s) older than {} days", removed, RETENTION_DAYS);
        }
    }
}
