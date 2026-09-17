/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;
import pride.bank.erp.exception.NotFoundException;

/**
 * Creation and deactivation of <b>department-level</b> stores.
 *
 * <p>Deliberately narrow. The other three kinds of store are not hand-created and must not be:
 * <ul>
 *   <li>a branch's location-level ADMIN store is guaranteed by {@code StoreSeeder};</li>
 *   <li>IT and Disposal are organisation-wide singletons at Head Office — a second one would make
 *       repair and disposal routing ambiguous;</li>
 *   <li>courier custody stores are created with their courier.</li>
 * </ul>
 *
 * <p>Stores are deactivated, never deleted, so historical movements keep resolving their source and
 * destination.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreAdminService {

    private final StoreRepository storeRepository;
    private final StoreBalanceRepository storeBalanceRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    /**
     * Refuses a change to a store the caller has no business touching.
     *
     * <h2>Two rules, because two kinds of store</h2>
     * Both entry points below reach a store <b>by id</b>, and until now neither asked whose it was.
     * {@code UPDATE_STORE} admitted the caller to the endpoint and nothing afterwards looked at the
     * store — so a Gulu officer could rename or deactivate Kampala's department store, or rename the
     * bank-wide IT store, simply by knowing its id. That is the same shape of hole assets had before
     * {@code requireAssetInScope}.
     *
     * <p><b>An ordinary store answers to its branch.</b> A department store at Gulu is Gulu's, and a
     * branch's ADMIN store likewise, so the location is the key.
     *
     * <p><b>The bank-wide singletons answer to nobody below ALL.</b> The IT and Disposal stores are
     * one each for the whole bank and every branch's repairs and write-offs route through them. They
     * stand at Head Office, so guarding by location alone would hand them to any Head Office officer
     * with branch reach — and one branch renaming or reactivating a store the other twenty depend on
     * is not a branch-sized decision. It needs the explicit cross-branch grant.
     */
    private void requireStoreChangeable(Store store) {
        boolean bankWide = store.getStoreType() == StoreType.IT
                || store.getStoreType() == StoreType.DISPOSAL;

        if (bankWide) {
            if (accessScopeService.scopeFor(pride.bank.erp.access.ScopeSubject.INVENTORY,
                    pride.bank.erp.access.ScopeAction.MANAGE)
                    != pride.bank.erp.dashboard.DashboardScope.ALL) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "'" + store.getName() + "' is shared by the whole bank. Changing it needs "
                                + "cross-branch inventory management.");
            }
            return;
        }

        accessScopeService.requireCanManage(
                pride.bank.erp.access.ScopeSubject.INVENTORY,
                store.getLocation() != null ? store.getLocation().getId() : null,
                null);
    }

    @Transactional
    public Store createDepartmentStore(StoreAdminDTO request) throws NotFoundException {
        if (request.getLocationId() == null) {
            throw new IllegalArgumentException("A store needs a location");
        }
        if (request.getDepartmentId() == null) {
            throw new IllegalArgumentException(
                    "A department is required — every branch already has a location-level Admin store");
        }

        Branch location = branchRepository.findById(request.getLocationId())
                .orElseThrow(() -> new NotFoundException("Branch " + request.getLocationId() + " not found"));
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Department " + request.getDepartmentId() + " not found"));

        storeRepository
                .findByLocation_IdAndStoreTypeAndDepartment_Id(location.getId(), StoreType.ADMIN, department.getId())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(String.format(
                            "%s already has a store at %s.", department.getName(), location.getName()));
                });

        String name = (request.getName() != null && !request.getName().isBlank())
                ? request.getName().trim()
                : location.getName() + " — " + department.getName() + " Store";

        Store saved = storeRepository.save(Store.builder()
                .location(location)
                .department(department)
                .storeType(StoreType.ADMIN)
                .name(name)
                .active(true)
                .build());

        log.info("Created department store {} ('{}') for department {} at {}",
                saved.getId(), name, department.getName(), location.getName());
        return saved;
    }

    /**
     * Activates or deactivates a store. Deactivation is refused while the store still holds stock —
     * hiding a store that has goods in it would strand them where nothing can find or issue them.
     */
    @Transactional
    public Store setActive(Long storeId, boolean active) throws NotFoundException {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store " + storeId + " not found"));
        requireStoreChangeable(store);

        if (!active) {
            if (store.getDepartment() == null) {
                throw new IllegalArgumentException(
                        "Only department stores can be deactivated — a branch's Admin store and the "
                                + "organisation's IT/Disposal stores are part of the core structure.");
            }
            int held = storeBalanceRepository.findByStore_Id(storeId).stream()
                    .mapToInt(StoreBalance::getQuantity).sum();
            if (held > 0) {
                throw new IllegalArgumentException(String.format(
                        "'%s' still holds %d unit(s). Move them out before deactivating it.",
                        store.getName(), held));
            }
        }

        store.setActive(active);
        return storeRepository.save(store);
    }

    @Transactional
    public Store rename(Long storeId, String name) throws NotFoundException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A store name cannot be empty");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("Store " + storeId + " not found"));
        requireStoreChangeable(store);
        store.setName(name.trim());
        return storeRepository.save(store);
    }
}
