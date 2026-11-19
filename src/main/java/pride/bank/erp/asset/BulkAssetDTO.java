/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the asset import spreadsheet.
 *
 * <p>Field names are the camel-cased column headers the browser produces from the template, so the
 * header text and these names have to stay in step — {@code AssetImportTemplate} on the frontend is
 * the other half of this contract.
 *
 * <p><b>Everything is a String.</b> Dates, money and quantities are all parsed defensively in
 * {@code AssetService} instead of being bound by Jackson. This is the difference between "row 412
 * has a date we could not read" and a single 400 that rejects a two-thousand-row file because one
 * cell was typed by hand. The previous version bound {@code dateOfPmVerification} as an
 * {@code Integer} Excel serial, so any user who typed a real date failed the whole import before a
 * single row was attempted.
 *
 * <p>Unknown properties are ignored: people add their own working columns to a spreadsheet, and a
 * stray "Comments" column should not fail an import.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkAssetDTO {

    /** The sheet's own "No." column, used to point at the offending row in the error report. */
    private Long no;

    // ── Identity ─────────────────────────────────────────────────────────────
    private String assetName;
    private String engravedNumber;
    private String serialNumber;
    private String tagName;

    // ── Classification ───────────────────────────────────────────────────────
    private String commodity;
    private String supplier;

    // ── Placement ────────────────────────────────────────────────────────────
    private String location;
    /** Staff number or email of the holder. Blank leaves the asset unassigned. */
    private String assignedTo;
    /** Status name or code. Blank falls back to "Require Update". */
    private String status;

    // ── Commercial ───────────────────────────────────────────────────────────
    private String dateReceived;
    private String purchaseCost;
    private String costOfTheAsset;
    private String netBookValue;
    private String depreciationRate;
    private String unitOfMeasure;
    /** Optional. The LPO, where an old record still has one; never required, never a link to an order. */
    private String lpoReference;

    // ── Descriptive ──────────────────────────────────────────────────────────
    private String make;
    private String model;
    private String description;

    // ── IT-specific (present only on categories whose fieldConfig shows them) ─
    private String hostname;
    private String ipAddress;
    private String macAddress;
    private String ram;
    private String cpuSpeed;
    private String hardDiskSize;
    private String interfaceType;
}
