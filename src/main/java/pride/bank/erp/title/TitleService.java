/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;
import pride.bank.erp.role.Role;
import pride.bank.erp.role.RoleService;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;


@Service
@RequiredArgsConstructor
public class TitleService {
    private final TitleSearchDao titleSearchDao;
    private final TitleRepository titleRepository;
    private final RoleService roleService;

    public Page<Title> fetchAllTitles(
            TitleQueryParams params
    ) {
        TitlePage titlePage = TitlePage.builder()
            .pageSize(params.getPageSize())
            .sortDirection(determineSortDirection(params.getSortDirection()))
            .sortBy(params.getSortBy())
            .pageNumber(params.getPageNumber())
            .build();

        TitleSearchCriteria titleSearchCriteria = TitleSearchCriteria
            .builder()
            .name(params.getName())
            .shortCode(params.getShortCode())
            .reportsToId(params.getReportsToId())
            .roleId(params.getRoleId())
            .build();
        return titleSearchDao.findAllWithFilters(titlePage, titleSearchCriteria);
    }

    @Transactional
    public Title createTitle(TitleRequestDTO request)
            throws NotFoundException {
        Title reportsTo = request.getReportsTo() == null ? null : findTitle(request.getReportsTo());
        Role role = roleService.findOneRole(request.getRole());
        ensureNoDuplicateTitle(request, reportsTo, null);

        Title title = Title.builder()
                .name(request.getName())
                .shortCode(request.getShortCode())
                .reportsTo(reportsTo)
                .role(role)
                .build();
        return titleRepository.save(title);
    }

    @Transactional
    public Title updateTitle(TitleRequestDTO request, Long id)
            throws NotFoundException {
        Title title = findTitle(id);
        Role role = roleService.findOneRole(request.getRole());
        Title reportsTo = request.getReportsTo() == null ? null : findTitle(request.getReportsTo());

        validateReportsToRelationship(title, reportsTo);
        ensureNoDuplicateTitle(request, reportsTo, id);

        title.setName(request.getName());
        title.setShortCode(request.getShortCode());
        title.setReportsTo(reportsTo);
        title.setRole(role);
        return titleRepository.save(title);

    }

    public Title findTitle(Long id) throws NotFoundException {
        return titleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Title with ID " + id + " not found"));
    }

    @Transactional
    public DeleteResponse deleteTitle(Long id) throws NotFoundException {
        if (!titleRepository.existsById(id)) {
            throw new NotFoundException("Title with ID " + id + " not found");
        }
        titleRepository.deleteById(id);
        return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    private void ensureNoDuplicateTitle(TitleRequestDTO request, Title reportsTo, Long currentTitleId) {
        var existingTitle = reportsTo == null
                ? titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsToIsNull(request.getName(), request.getShortCode())
                : titleRepository.findByNameIgnoreCaseAndShortCodeIgnoreCaseAndReportsTo_Id(
                        request.getName(),
                        request.getShortCode(),
                        reportsTo.getId()
                );

        if (existingTitle.isPresent() && !existingTitle.get().getId().equals(currentTitleId)) {
            throw new IllegalArgumentException(
                    "A title with name '" + request.getName() + "' and short code '" + request.getShortCode() + "' already exists"
            );
        }
    }

    private void validateReportsToRelationship(Title title, Title reportsTo) {
        if (reportsTo == null) {
            return;
        }

        if (title.getId().equals(reportsTo.getId())) {
            throw new IllegalArgumentException("A title cannot report to itself");
        }

        Title current = reportsTo;
        while (current != null) {
            if (title.getId().equals(current.getId())) {
                throw new IllegalArgumentException("A title cannot report to one of its subordinates");
            }
            current = current.getReportsTo();
        }
    }
}
