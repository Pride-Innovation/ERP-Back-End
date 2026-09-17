/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetTypeCustomAttributeRepository extends JpaRepository<AssetTypeCustomAttribute, Long> {
    List<AssetTypeCustomAttribute> findByAssetTypeIdOrderByDisplayOrderAsc(Long assetTypeId);
}
