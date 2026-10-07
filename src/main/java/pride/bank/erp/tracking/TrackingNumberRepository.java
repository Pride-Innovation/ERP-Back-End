/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackingNumberRepository extends JpaRepository<TrackingNumber, Long> {

    Optional<TrackingNumber> findFirstByOwnerTypeAndOwnerIdAndStatusOrderByIdDesc(
            TrackingNumber.OwnerType ownerType, Long ownerId, TrackingNumber.Status status);

    Optional<TrackingNumber> findByNumber(String number);
}
