/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.commodity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommodityRepository extends JpaRepository<Commodity, Long> {
	Optional<Commodity> findByNameIgnoreCaseAndGroupNameIgnoreCaseAndAssetType_Id(String name, String groupName, Long assetTypeId);
	List<Commodity> findAllByNameIgnoreCase(String name);

	/**
	 * Commodities belonging to one asset category.
	 *
	 * <p>Used both to fill the import template's Commodity dropdown and to resolve a row's value.
	 * Scoped to the category because a commodity name is only unique within one — the import runs
	 * against a single category, so an unscoped match could bind an asset to another category's
	 * commodity and quietly corrupt the classification.
	 */
	List<Commodity> findAllByAssetType_Id(Long assetTypeId);

	Optional<Commodity> findByNameIgnoreCaseAndAssetType_Id(String name, Long assetTypeId);

	/** Every commodity of that name in the category, oldest first - the seeder's lookup, tolerant of duplicates. */
	List<Commodity> findAllByNameIgnoreCaseAndAssetType_IdOrderByIdAsc(String name, Long assetTypeId);
}
