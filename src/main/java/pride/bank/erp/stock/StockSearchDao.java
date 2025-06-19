package pride.bank.erp.stock;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pride.bank.erp.helper.Constants.*;

@Repository
public class StockSearchDao {

    private final EntityManager entityManager;
    private final CriteriaBuilder criteriaBuilder;

    public StockSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.criteriaBuilder = entityManager.getCriteriaBuilder();
    }

    public Page<Stock> findAllWithFilters(
            StockPage stockPage,
            StockSearchCriteria stockSearchCriteria
    ) {
        CriteriaQuery<Stock> criteriaQuery = criteriaBuilder.createQuery(Stock.class);
        Root<Stock> stockRoot = criteriaQuery.from(Stock.class);

        Predicate predicate = getPredicate(stockSearchCriteria, stockRoot);
        criteriaQuery.where(predicate);

        setOrder(stockPage, criteriaQuery, stockRoot);

        TypedQuery<Stock> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(stockPage) * pageSize(stockPage));
        typedQuery.setMaxResults(pageSize(stockPage));

        Pageable pageable = getPageable(stockPage);
        long stocksCount = getStocksCount(stockSearchCriteria);

        return new PageImpl<>(typedQuery.getResultList(), pageable, stocksCount);
    }

    private Predicate getPredicate(StockSearchCriteria criteria, Root<Stock> root) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(criteria.getLPONumber())) {
            predicates.add(criteriaBuilder.like(root.get("LPONumber"), "%" + criteria.getLPONumber() + "%"));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    private void setOrder(StockPage stockPage, CriteriaQuery<Stock> query, Root<Stock> root) {
        if (sortDirection(stockPage).equals(Sort.Direction.ASC)) {
            query.orderBy(criteriaBuilder.asc(root.get(sortBy(stockPage))));
        } else {
            query.orderBy(criteriaBuilder.desc(root.get(sortBy(stockPage))));
        }
    }

    private Pageable getPageable(StockPage stockPage) {
        Sort sort = Sort.by(sortDirection(stockPage), sortBy(stockPage));
        return PageRequest.of(pageNumber(stockPage), pageSize(stockPage), sort);
    }

    private long getStocksCount(StockSearchCriteria criteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Stock> countRoot = countQuery.from(Stock.class);

        Predicate countPredicate = getPredicate(criteria, countRoot);
        countQuery.select(criteriaBuilder.count(countRoot)).where(countPredicate);

        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private Sort.Direction sortDirection(StockPage stockPage) {
        return stockPage.getSortDirection() != null ? stockPage.getSortDirection() : Sort.Direction.ASC;
    }

    private String sortBy(StockPage stockPage) {
        return stockPage.getSortBy() != null ? stockPage.getSortBy() : DEFAULT_SORT_FIELD;
    }

    private Integer pageSize(StockPage stockPage) {
        return stockPage.getPageSize() != null ? stockPage.getPageSize() : DEFAULT_PAGE_SIZE;
    }

    private Integer pageNumber(StockPage stockPage) {
        return stockPage.getPageNumber() != null ? stockPage.getPageNumber() : DEFAULT_PAGE_NUMBER;
    }
}
