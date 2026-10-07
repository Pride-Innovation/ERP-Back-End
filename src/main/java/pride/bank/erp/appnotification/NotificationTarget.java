/*
13.9 Pride's Standard Copyright Notice:
Copyright (c)20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.appnotification;

/**
 * What a notification is about.
 *
 * <h2>Why this replaced a URL</h2>
 * Notifications used to carry a fully-formed frontend link, built in Java from a configured host.
 * Measured when that was removed: <b>744 of 760 rows pointed at a route that does not exist</b> -
 * they used a query parameter where the route takes a path segment - and all 760 were absolute URLs
 * that no router could follow. Nobody noticed, because nothing ever followed them.
 *
 * <p>Java cannot know when a frontend route is renamed, so a URL written here is a guess that ages.
 * Naming the <em>subject</em> instead moves that decision to the one place that owns routes, and a
 * renamed route becomes a one-line change rather than a silent breakage across a table.
 *
 * <p>Kept as a string on the wire rather than an enum the frontend must mirror: a new kind of
 * notification should render as plain, unclickable text on an older client rather than break it.
 */
public enum NotificationTarget {

    /** An asset request - everything the workflow engine raises. */
    REQUEST,

    /** A stock movement - everything the movement approval service raises. */
    MOVEMENT,

    /**
     * A temporary replacement still out with the recipient - the entity id is the loaner asset.
     * Raised when their own asset comes back from repair, as a reminder to hand the loaner in.
     */
    LOANER
}
