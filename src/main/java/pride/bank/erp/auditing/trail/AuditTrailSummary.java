/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.trail;

/**
 * The four tiles above the Audit Trails table.
 *
 * <p>A separate endpoint because the table is paged in SQL: with fifty rows in hand the page cannot
 * derive "12,480 total events" or "31 active users" by counting what it was sent. These have to be
 * counted over the whole table, and counting is all this does.
 *
 * @param totalEvents   every event ever recorded
 * @param eventsToday   since midnight, local time
 * @param activeActors  distinct people appearing in the log today
 * @param flaggedEvents everything above {@code INFO}, all time
 */
public record AuditTrailSummary(
        long totalEvents,
        long eventsToday,
        long activeActors,
        long flaggedEvents
) {
}
