/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.status;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.helper.DeleteResponse;


@Service
@RequiredArgsConstructor
public class StatusService {
    private final StatusRepository statusRepository;

    public Page<Status> fetchAllStatuses(Pageable pageable) {
        return statusRepository.findAll(pageable);
    }

    public Status createStatus(Status request) {
        Status status = Status.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status(request.getStatus())
                .build();
        return statusRepository.save(status);
    }

    public Status updateStatus(Status request, Long id) throws NotFoundException {
        Status status = findOneStatus(id);
        status.setStatus(request.getStatus());
        status.setName(request.getName());
        status.setDescription(request.getDescription());
        return statusRepository.save(status);
    }

    public Status findOneStatus(Long statusId) throws NotFoundException {
        return statusRepository.findById(statusId)
                .orElseThrow(() -> new NotFoundException("Status with ID " + statusId + " is not found"));
    }

    public DeleteResponse deleteStatus(Long statusId) {
        if (statusRepository.existsById(statusId)) {
            statusRepository.deleteById(statusId);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }
}
