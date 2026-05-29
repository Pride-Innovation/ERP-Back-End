/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UnitService {
    private final UnitRepository unitRepository;
    private final DepartmentService departmentService;

    public Page<UnitResponse> fetchAllUnits(UnitQueryParams params) {
        Sort sort = Sort.by(
                Constants.ASCENDING_SORT_DIRECTION.equalsIgnoreCase(params.getSortDirection())
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                params.getSortBy()
        );
        var pageable = PageRequest.of(params.getPageNumber(), params.getPageSize(), sort);

        boolean hasName = params.getName() != null && !params.getName().isBlank();
        boolean hasDepartment = params.getDepartmentId() != null;

        Page<Unit> page;
        if (hasName && hasDepartment) {
            page = unitRepository.findAllByNameContainingIgnoreCaseAndDepartment_Id(
                    params.getName(), params.getDepartmentId(), pageable);
        } else if (hasDepartment) {
            page = unitRepository.findAllByDepartment_Id(params.getDepartmentId(), pageable);
        } else if (hasName) {
            page = unitRepository.findAllByNameContainingIgnoreCase(params.getName(), pageable);
        } else {
            page = unitRepository.findAll(pageable);
        }
        return page.map(UnitResponse::from);
    }

    public Unit findOneUnit(Long id) throws NotFoundException {
        return unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Unit with Id %s is not found", id)));
    }

    public UnitResponse findOneUnitResponse(Long id) throws NotFoundException {
        return UnitResponse.from(findOneUnit(id));
    }

    @Transactional
    public UnitResponse createUnit(UnitDTO request) throws NotFoundException {
        Department department = departmentService.findOneDepartment(request.getDepartmentId());
        ensureNoDuplicateUnitName(request.getName(), request.getDepartmentId(), null);
        Unit unit = Unit.builder()
                .name(request.getName())
                .groupEmail(request.getGroupEmail())
                .department(department)
                .build();
        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitResponse updateUnit(UnitDTO request, Long id) throws NotFoundException {
        Unit unit = findOneUnit(id);
        Department department = departmentService.findOneDepartment(request.getDepartmentId());
        ensureNoDuplicateUnitName(request.getName(), request.getDepartmentId(), id);
        unit.setName(request.getName());
        unit.setGroupEmail(request.getGroupEmail());
        unit.setDepartment(department);
        return UnitResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public DeleteResponse deleteUnit(Long id) throws NotFoundException {
        findOneUnit(id);
        unitRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    /** Unit names must be unique within a department (the same name may exist under another department). */
    private void ensureNoDuplicateUnitName(String name, Long departmentId, Long excludeId) {
        Optional<Unit> existing = unitRepository.findByNameIgnoreCaseAndDepartment_Id(name, departmentId);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw new IllegalArgumentException(
                    String.format("A unit named '%s' already exists in this department", name));
        }
    }
}
