/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

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
public class TitleController {
    private final TitleService titleService;

    @GetMapping("/titles")
    public ResponseEntity<Page<Title>> fetchAllTitles(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = ASCENDING_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false) String name
    ) {
        Page<Title> response = titleService.fetchAllTitles(
                pageSize,
                sortDirection,
                pageNumber,
                sortBy,
                name
        );

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/titles")
    public ResponseEntity<Title> createTitle(
            @RequestBody TitleRequestDTO titleRequestDTO
    )
            throws NotFoundException {
        Title response = titleService.createTitle(titleRequestDTO);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @PutMapping("/titles/{id}")
    public ResponseEntity<Title> updateTitle(
            @RequestBody TitleRequestDTO titleRequestDTO,
            @PathVariable Long id
    )
            throws NotFoundException {
        Title response = titleService.updateTitle(titleRequestDTO, id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(201));
    }

    @GetMapping("/titles/{id}")
    public ResponseEntity<Title> getSingleTitle(@PathVariable Long id) throws NotFoundException {
        return new ResponseEntity<>(titleService.findTitle(id), HttpStatus.OK);
    }

    @DeleteMapping("/titles/{id}")
    public ResponseEntity<DeleteResponse> deleteTitle(@PathVariable Long id) {
        DeleteResponse response = titleService.deleteTitle(id);
        return new ResponseEntity<>(response, HttpStatusCode.valueOf(204));
    }
}
