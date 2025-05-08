/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.Objects;

import static pride.bank.erp.helper.Constants.DESCENDING_SORT_DIRECTION;

@Service
@RequiredArgsConstructor
public class BranchService {
    private final BranchRepository branchRepository;
    private final BranchSearchDao branchSearchDao;

    public Page<Branch> fetchAllBranches(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            BranchPage branchPage = BranchPage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            BranchSearchCriteria branchSearchCriteria = BranchSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return branchSearchDao.findAllWithFilters(branchPage, branchSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public Branch createBranch(Branch request) {
        Branch branch = Branch.builder()
                .name(request.getName())
                .build();
        return branchRepository.save(branch);
    }

    public Branch updateBranch(Branch request, Long id) throws NotFoundException {
        Branch branch = findBranch(id);
        branch.setName(request.getName());
        return branchRepository.save(branch);

    }

    public Branch findBranch(Long id) throws NotFoundException {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Branch with ID " + id + " not found"));
    }

    public DeleteResponse deleteBranch(Long id) {
        if (branchRepository.existsById(id)) {
            branchRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
