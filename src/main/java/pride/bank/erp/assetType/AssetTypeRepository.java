/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {
    Optional<AssetType> findByName(String name);
    Optional<AssetType> findByNameIgnoreCase(String name);
    Optional<AssetType> findByShortCode(String shortCode);
    Page<AssetType> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByShortCodeIgnoreCaseAndIdNot(String shortCode, Long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByShortCodeIgnoreCase(String shortCode);
}

