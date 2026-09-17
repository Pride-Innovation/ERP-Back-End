/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exportconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pride.bank.erp.helper.Constants;

import java.util.List;
import java.util.Map;

/**
 * Which columns each table puts into its exported PDF / Excel.
 *
 * <h2>The read is deliberately open to any signed-in user</h2>
 * Every export in the application has to consult this before building a file, so gating the read on
 * the permission that <em>changes</em> it would break exporting for everyone who is not an
 * administrator — the page would open, the download would 403, and that reads as a broken button
 * rather than a boundary. This codebase has made that exact mistake three times (the route's
 * permission and the endpoint's permission must be the same permission), most recently on Stock
 * Take, so it is worth naming here.
 *
 * <p>Nothing is disclosed by the read: it returns a list of column names for tables the caller can
 * already open, and those columns are already on their screen.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class ExportColumnConfigController {

    private final ExportColumnConfigService service;

    /**
     * Every table's configured columns, keyed by table.
     *
     * <p>A table missing from the response is <b>unconfigured</b>, and the client exports all of its
     * columns — the widest answer, chosen deliberately so that shipping this does not blank every
     * export in the bank on day one.
     */
    @GetMapping("/export-columns")
    public ResponseEntity<Map<String, List<String>>> fetchAll() {
        return ResponseEntity.ok(service.fetchAll());
    }

    /**
     * Replaces one table's columns with exactly those given, in the order given.
     *
     * <p>A replacement, like {@code PUT /units/{id}/roles}: send the set you want. Adding and
     * removing as one operation means there is no second call anyone can forget, and no way to leave
     * a column in every printed report because only half the change was made.
     */
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_EXPORT_COLUMNS + "')")
    @PutMapping("/export-columns/{tableKey}")
    public ResponseEntity<List<String>> replace(
            @PathVariable String tableKey,
            @RequestBody List<String> columnKeys
    ) {
        return ResponseEntity.ok(service.replace(tableKey, columnKeys));
    }

    /**
     * Puts a table back to exporting every column it has.
     *
     * <p>Distinct from configuring it down to a minimum — this is "stop curating this table", and it
     * restores the unconfigured state rather than saving a narrow one.
     */
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_EXPORT_COLUMNS + "')")
    @DeleteMapping("/export-columns/{tableKey}")
    public ResponseEntity<Void> reset(@PathVariable String tableKey) {
        service.reset(tableKey);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
