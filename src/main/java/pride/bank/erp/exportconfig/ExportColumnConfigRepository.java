/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exportconfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ExportColumnConfigRepository extends JpaRepository<ExportColumnConfig, Long> {

    /** Every configured column, in the order the exported file should carry them. */
    List<ExportColumnConfig> findAllByOrderByTableKeyAscSortOrderAsc();

    /** One table's configured columns, in file order. */
    List<ExportColumnConfig> findByTableKeyOrderBySortOrderAsc(String tableKey);

    /**
     * Clears one table's configuration so it can be written afresh.
     *
     * <p>Saving is a replacement rather than a merge — send the columns you want, and what you did
     * not send is gone. The alternative is two operations, one of which somebody eventually forgets;
     * the same reasoning as {@code PUT /units/{id}/roles}.
     */
    @Transactional
    void deleteByTableKey(String tableKey);
}
