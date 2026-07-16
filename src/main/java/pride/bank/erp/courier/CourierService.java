/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.courier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;

import java.util.List;

/**
 * Registry of couriers — vetted (registered ahead of time via Settings) or ad-hoc (auto-created the
 * first time a dispatcher types an unrecognized name at dispatch). Every courier gets exactly one
 * custody {@link Store} ({@link StoreType#COURIER}), bootstrapped here, so both kinds are tracked
 * identically once a movement is handed to them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourierService {

    private final CourierRepository courierRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;

    public List<CourierDTO> fetchAll(boolean activeOnly) {
        List<Courier> couriers = activeOnly
                ? courierRepository.findByActiveTrueOrderByNameAsc()
                : courierRepository.findAll();
        return couriers.stream().map(this::toDTO).toList();
    }

    public CourierDTO getOne(Long id) throws NotFoundException {
        return toDTO(find(id));
    }

    @Transactional
    public CourierDTO create(CourierDTO request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Courier name is required");
        }
        if (courierRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A courier named '" + request.getName() + "' already exists");
        }
        Courier courier = Courier.builder()
                .name(request.getName().trim())
                .contactPerson(trimOrNull(request.getContactPerson()))
                .phone(trimOrNull(request.getPhone()))
                .email(trimOrNull(request.getEmail()))
                .address(trimOrNull(request.getAddress()))
                .vetted(request.getVetted() == null || request.getVetted())
                .active(request.getActive() == null || request.getActive())
                .build();
        Courier saved = courierRepository.save(courier);
        ensureCustodyStore(saved);
        return toDTO(saved);
    }

    @Transactional
    public CourierDTO update(Long id, CourierDTO request) throws NotFoundException {
        Courier courier = find(id);
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Courier name is required");
        }
        if (courierRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A courier named '" + request.getName() + "' already exists");
        }
        courier.setName(request.getName().trim());
        courier.setContactPerson(trimOrNull(request.getContactPerson()));
        courier.setPhone(trimOrNull(request.getPhone()));
        courier.setEmail(trimOrNull(request.getEmail()));
        courier.setAddress(trimOrNull(request.getAddress()));
        if (request.getVetted() != null) {
            courier.setVetted(request.getVetted());
        }
        if (request.getActive() != null) {
            courier.setActive(request.getActive());
        }
        return toDTO(courierRepository.save(courier));
    }

    public DeleteResponse delete(Long id) throws NotFoundException {
        if (!courierRepository.existsById(id)) {
            throw new NotFoundException("Courier " + id + " not found");
        }
        try {
            courierRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } catch (Exception e) {
            // Still referenced by movements — deactivate instead of hard delete.
            Courier courier = courierRepository.findById(id).orElse(null);
            if (courier != null) {
                courier.setActive(false);
                courierRepository.save(courier);
                return new DeleteResponse("Courier is in use and was deactivated instead of deleted", true);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete this courier");
        }
    }

    /**
     * Resolves a courier for dispatch by (vetted) id, or by name — creating an unvetted courier on
     * the fly if no vetted one matches. Either way the returned courier has a custody store.
     */
    @Transactional
    public Courier resolveForDispatch(Long courierId, String courierName) throws NotFoundException {
        if (courierId != null) {
            return find(courierId);
        }
        if (!StringUtils.hasText(courierName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A courier is required to dispatch");
        }
        String trimmed = courierName.trim();
        return courierRepository.findByNameIgnoreCase(trimmed).orElseGet(() -> {
            Courier adHoc = courierRepository.save(Courier.builder()
                    .name(trimmed)
                    .vetted(false)
                    .active(true)
                    .build());
            ensureCustodyStore(adHoc);
            log.info("Auto-created ad-hoc courier '{}' (id={})", trimmed, adHoc.getId());
            return adHoc;
        });
    }

    Courier find(Long id) throws NotFoundException {
        return courierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Courier " + id + " not found"));
    }

    /** Creates the courier's custody store if it doesn't already have one. Idempotent. */
    private void ensureCustodyStore(Courier courier) {
        if (storeRepository.existsByCourier_Id(courier.getId())) {
            return;
        }
        Branch headOffice = branchRepository.findFirstByIsHeadOfficeTrue()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "No Head Office branch is configured"));
        storeRepository.save(Store.builder()
                .name(courier.getName() + " — In Transit")
                .storeType(StoreType.COURIER)
                .location(headOffice)
                .courier(courier)
                .active(true)
                .build());
    }

    private String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private CourierDTO toDTO(Courier c) {
        return CourierDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .contactPerson(c.getContactPerson())
                .phone(c.getPhone())
                .email(c.getEmail())
                .address(c.getAddress())
                .vetted(c.isVetted())
                .active(c.isActive())
                .build();
    }
}
