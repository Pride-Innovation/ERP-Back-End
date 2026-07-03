/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.consultant;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultantService {

    private final ConsultantRepository consultantRepository;

    public List<ConsultantDTO> fetchAll(boolean activeOnly) {
        List<Consultant> consultants = activeOnly
                ? consultantRepository.findByActiveTrueOrderByNameAsc()
                : consultantRepository.findAll();
        return consultants.stream().map(this::toDTO).toList();
    }

    public ConsultantDTO getOne(Long id) throws NotFoundException {
        return toDTO(find(id));
    }

    public ConsultantDTO create(ConsultantDTO request) {
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultant name is required");
        }
        if (consultantRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A consultant named '" + request.getName() + "' already exists");
        }
        Consultant consultant = Consultant.builder()
                .name(request.getName().trim())
                .contactPerson(trimOrNull(request.getContactPerson()))
                .phone(trimOrNull(request.getPhone()))
                .email(trimOrNull(request.getEmail()))
                .address(trimOrNull(request.getAddress()))
                .specialization(trimOrNull(request.getSpecialization()))
                .active(request.getActive() == null || request.getActive())
                .build();
        return toDTO(consultantRepository.save(consultant));
    }

    public ConsultantDTO update(Long id, ConsultantDTO request) throws NotFoundException {
        Consultant consultant = find(id);
        if (!StringUtils.hasText(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consultant name is required");
        }
        if (consultantRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A consultant named '" + request.getName() + "' already exists");
        }
        consultant.setName(request.getName().trim());
        consultant.setContactPerson(trimOrNull(request.getContactPerson()));
        consultant.setPhone(trimOrNull(request.getPhone()));
        consultant.setEmail(trimOrNull(request.getEmail()));
        consultant.setAddress(trimOrNull(request.getAddress()));
        consultant.setSpecialization(trimOrNull(request.getSpecialization()));
        if (request.getActive() != null) {
            consultant.setActive(request.getActive());
        }
        return toDTO(consultantRepository.save(consultant));
    }

    public DeleteResponse delete(Long id) throws NotFoundException {
        if (!consultantRepository.existsById(id)) {
            throw new NotFoundException("Consultant " + id + " not found");
        }
        try {
            consultantRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } catch (Exception e) {
            // Still referenced by repair movements — deactivate instead of hard delete.
            Consultant consultant = consultantRepository.findById(id).orElse(null);
            if (consultant != null) {
                consultant.setActive(false);
                consultantRepository.save(consultant);
                return new DeleteResponse("Consultant is in use and was deactivated instead of deleted", true);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete this consultant");
        }
    }

    Consultant find(Long id) throws NotFoundException {
        return consultantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Consultant " + id + " not found"));
    }

    private String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private ConsultantDTO toDTO(Consultant c) {
        return ConsultantDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .contactPerson(c.getContactPerson())
                .phone(c.getPhone())
                .email(c.getEmail())
                .address(c.getAddress())
                .specialization(c.getSpecialization())
                .active(c.isActive())
                .build();
    }
}
