/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.unitofmeasure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The list of units of measure, and the one rule every place that stores a unit goes through.
 *
 * <p>Before this list a commodity's unit was free text and an asset's was a list of four written
 * into the page, and neither knew about the other: measured on UAT, commodities said "ream" where
 * the asset list said "reams", 28 imported assets said "piece", and a received asset was given no
 * unit at all. {@link #resolve} is now the gate for commodities, the asset form and the import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitOfMeasureService {

    static final int MAX_LENGTH = 50;

    private final UnitOfMeasureRepository unitRepository;
    private final CommodityRepository commodityRepository;
    private final AuditService auditService;

    /** Trimmed, lower-case, single-spaced; blank is null. The stored form of every unit. */
    public static String normalise(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\s+", " ").toLowerCase();
        return value.isEmpty() ? null : value;
    }

    public List<UnitOfMeasure> list(boolean includeInactive) {
        return includeInactive
                ? unitRepository.findAllByOrderByNameAsc()
                : unitRepository.findAllByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public UnitOfMeasure create(String rawName) {
        String name = normalise(rawName);
        if (name == null) throw new BadRequestException("Enter the unit's name, for example \"pieces\".");
        if (name.length() > MAX_LENGTH) {
            throw new BadRequestException("A unit's name may be at most " + MAX_LENGTH + " characters.");
        }
        Optional<UnitOfMeasure> existing = unitRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            throw new BadRequestException(existing.get().isActive()
                    ? "\"" + name + "\" is already on the list."
                    : "\"" + name + "\" is already on the list but switched off. Switch it back on instead.");
        }
        UnitOfMeasure saved = unitRepository.save(UnitOfMeasure.builder().name(name).active(true).build());
        auditService.record(AuditEventType.CREATED, AuditModule.SYSTEM, "UNIT_OF_MEASURE_ADDED",
                "Unit of measure \"" + name + "\" added", AuditSeverity.INFO, "UnitOfMeasure", saved.getId());
        return saved;
    }

    @Transactional
    public UnitOfMeasure setActive(Long id, boolean active) throws NotFoundException {
        UnitOfMeasure unit = unitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Unit of measure " + id + " was not found"));
        if (unit.isActive() == active) return unit;
        unit.setActive(active);
        UnitOfMeasure saved = unitRepository.save(unit);
        auditService.record(AuditEventType.UPDATED, AuditModule.SYSTEM,
                active ? "UNIT_OF_MEASURE_ACTIVATED" : "UNIT_OF_MEASURE_DEACTIVATED",
                "Unit of measure \"" + unit.getName() + "\" switched " + (active ? "on" : "off"),
                AuditSeverity.INFO, "UnitOfMeasure", saved.getId());
        return saved;
    }

    /**
     * The stored form of a unit someone chose, or a refusal naming what may be chosen.
     *
     * <p>Blank is null - the caller decides what that means. A value equal to {@code current} is
     * accepted even if it has since been switched off, or is not on the list at all: re-saving a
     * record must never fail over a unit it already carried. Anything else must be an active unit.
     */
    public String resolve(String raw, String current) {
        String value = normalise(raw);
        if (value == null) return null;
        if (value.equals(normalise(current))) return value;

        Optional<UnitOfMeasure> unit = unitRepository.findByNameIgnoreCase(value);
        if (unit.isEmpty()) {
            throw new BadRequestException("\"" + raw.trim() + "\" is not a unit of measure. Choose one of: "
                    + activeNames() + " - or add it in Settings → Units of Measure.");
        }
        if (!unit.get().isActive()) {
            throw new BadRequestException("\"" + unit.get().getName()
                    + "\" has been switched off in Settings → Units of Measure.");
        }
        return unit.get().getName();
    }

    /** Active unit names, for the import template's dropdown. */
    public List<String> activeNameList() {
        return unitRepository.findAllByActiveTrueOrderByNameAsc().stream().map(UnitOfMeasure::getName).toList();
    }

    private String activeNames() {
        String names = activeNameList().stream().collect(Collectors.joining(", "));
        return names.isEmpty() ? "(none yet)" : names;
    }

    /**
     * Adds any unit a commodity uses that the list does not have. Create-only and run on startup,
     * after the commodities are seeded: it fills a new database's list, and never re-enables a unit
     * someone switched off (that row exists, so it is left alone).
     */
    @Transactional
    public void seedFromCommodities() {
        List<String> used = commodityRepository.findAll().stream()
                .map(Commodity::getGroupName)
                .map(UnitOfMeasureService::normalise)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (String name : used) {
            if (name.length() > MAX_LENGTH) continue;
            if (unitRepository.findByNameIgnoreCase(name).isEmpty()) {
                unitRepository.save(UnitOfMeasure.builder().name(name).active(true).build());
                log.info("Unit of measure \"{}\" added from the commodity list", name);
            }
        }
    }
}
