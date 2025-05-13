/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.branch;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.DeleteResponse;

import static pride.bank.erp.helper.Constants.ASCENDING_SORT_DIRECTION;
import static pride.bank.erp.helper.Constants.GENERAL_ROUTE;

@RestController
@RequestMapping(GENERAL_ROUTE)
@RequiredArgsConstructor
public class BranchController {
    private final BranchService branchService;

    @GetMapping("/branches")
    public ResponseEntity<Page<Branch>> fetchAllBranches(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {
        Page<Branch> response = branchService.fetchAllBranches(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name
        );

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/branches")
    public ResponseEntity<Branch> createBranch(@RequestBody BranchDTO branch)
            throws NotFoundException {
        Branch response = branchService.createBranch(branch);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/branches/{id}")
    public ResponseEntity<Branch> updateBranch(@RequestBody Branch branch, @PathVariable Long id)
            throws NotFoundException {
        Branch response = branchService.updateBranch(branch, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/branches/{id}")
    public ResponseEntity<Branch> getSingleBranch(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(branchService.findBranch(id), HttpStatus.OK);
    }

    @DeleteMapping("/branches/{id}")
    public ResponseEntity<DeleteResponse> deleteBranch(@PathVariable Long id) {
        DeleteResponse response = branchService.deleteBranch(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }
}
