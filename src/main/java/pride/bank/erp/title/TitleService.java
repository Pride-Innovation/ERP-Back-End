package pride.bank.erp.title;

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
public class TitleService {
    private final TitleSearchDao titleSearchDao;
    private final TitleRepository titleRepository;

    public Page<Title> fetchAllTitles(
            int pageSize,
            String sortDirection,
            int pageNumber,
            String sortBy,
            String name
    ) {
        try {
            TitlePage titlePage = TitlePage.builder()
                    .pageSize(pageSize)
                    .sortDirection(determineSortDirection(sortDirection))
                    .sortBy(sortBy)
                    .pageNumber(pageNumber)
                    .build();

            TitleSearchCriteria titleSearchCriteria = TitleSearchCriteria
                    .builder()
                    .name(name)
                    .build();
            return titleSearchDao.findAllWithFilters(titlePage, titleSearchCriteria);
        } catch (Exception e) {
            return null;
        }
    }

    public Title createTitle(TitleRequestDTO request)
            throws NotFoundException {
        Title reportsTo = findTitle(request.getReportsToID());
        Title title = Title.builder()
                .name(request.getName())
                .reportsTo(reportsTo)
                .build();
        return titleRepository.save(title);
    }

    public Title updateTitle(TitleRequestDTO request, Long id)
            throws NotFoundException {
        Title title = findTitle(id);

        if (request.getReportsToID() != null) {
            Title reportsTo = findTitle(request.getReportsToID());
            title.setReportsTo(reportsTo);
        }

        title.setName(request.getName());
        return titleRepository.save(title);

    }

    public Title findTitle(Long id) throws NotFoundException {
        return titleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Title with ID " + id + " not found"));
    }

    public DeleteResponse deleteTitle(Long id) {
        if (titleRepository.existsById(id)) {
            titleRepository.deleteById(id);
            return new DeleteResponse(Constants.DELETED_SUCCESSFULLY, true);
        } else {
            return new DeleteResponse(Constants.ENTITY_NOT_FOUND, false);
        }
    }

    private Sort.Direction determineSortDirection(String direction) {
        return Objects.equals(direction, DESCENDING_SORT_DIRECTION) ? Sort.Direction.DESC : Sort.Direction.ASC;
    }
}
