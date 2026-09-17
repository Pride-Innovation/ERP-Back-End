/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.dashboard;

/**
 * How far a dashboard widget may see.
 *
 * <p>A ladder, not a set: {@code ALL} includes everything {@code BRANCH} shows, which includes
 * everything {@code SELF} shows. Where a user holds more than one scope permission the highest
 * wins, so granting BRANCH to someone who already has SELF simply widens them rather than creating
 * a contradiction to resolve.
 *
 * <p>This is the axis the application was missing. It had exactly two settings — the user's own
 * branch, and (via {@code VIEW_ALL_BRANCHES}) everything — and no way to say "only what is mine",
 * which is precisely what a branch officer's dashboard should show.
 */
public enum DashboardScope {

    /** Only records the user personally owns: assets assigned to them, requests they raised. */
    SELF(0),

    /** Everything at the user's duty station. */
    BRANCH(1),

    /** Every branch and Head Office, with a branch selector to focus one at a time. */
    ALL(2);

    private final int rank;

    DashboardScope(int rank) {
        this.rank = rank;
    }

    /** The wider of two scopes. Used to collapse several granted scopes into one effective value. */
    public DashboardScope widest(DashboardScope other) {
        if (other == null) return this;
        return other.rank > this.rank ? other : this;
    }

    public boolean atLeast(DashboardScope minimum) {
        return this.rank >= minimum.rank;
    }
}
