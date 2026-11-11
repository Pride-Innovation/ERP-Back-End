/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

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

        Predicate predicate = getPredicate(stockSearchCriteria, stockRoot, criteriaQuery);
        criteriaQuery.where(predicate);

        setOrder(stockPage, criteriaQuery, stockRoot);

        TypedQuery<Stock> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult(pageNumber(stockPage) * pageSize(stockPage));
        typedQuery.setMaxResults(pageSize(stockPage));

        Pageable pageable = getPageable(stockPage);
        long stocksCount = getStocksCount(stockSearchCriteria);

        return new PageImpl<>(typedQuery.getResultList(), pageable, stocksCount);
    }

    private Predicate getPredicate(StockSearchCriteria criteria, Root<Stock> root, AbstractQuery<?> query) {
        List<Predicate> predicates = new ArrayList<>();

        if (Objects.nonNull(criteria.getScopedBranchId())) {
            /*
             * Branch scope, via an explicit LEFT JOIN.
             *
             * `branch` is NOT NULL on the entity, so an inner join would be harmless today — but the
             * next optional filter through a nullable association would inherit the pattern, and
             * that is how this codebase has been bitten four times. The join is written the safe way
             * so the shape being copied is the safe one.
             */
            predicates.add(criteriaBuilder.equal(
                    root.join("branch", jakarta.persistence.criteria.JoinType.LEFT).get("id"),
                    criteria.getScopedBranchId()));
        }

        if (Objects.nonNull(criteria.getLpoNumber())) {
            predicates.add(criteriaBuilder.like(root.get("lpoNumber"), "%" + criteria.getLpoNumber() + "%"));
        }

        if (Objects.nonNull(criteria.getStockStatusId())) {
            predicates.add(
                    criteriaBuilder.equal(root.get("status").get("id"), criteria.getStockStatusId())
            );
        }

        if (Objects.nonNull(criteria.getSupplierId())) {
            /*
             * An explicit LEFT JOIN, not `root.get("supplier").get("id")`.
             *
             * The dotted path would put an INNER JOIN in the FROM clause on every call, filtering
             * before this `nonNull` check is reached — so a stock row with no supplier would vanish
             * from the listing even when nobody is filtering by supplier. That is trap #1, and an
             * optional filter through a nullable association is exactly its shape.
             */
            predicates.add(criteriaBuilder.equal(
                    root.join("supplier", jakarta.persistence.criteria.JoinType.LEFT).get("id"),
                    criteria.getSupplierId()));
        }

        if (Objects.nonNull(criteria.getDisposed())) {
            predicates.add(criteriaBuilder.equal(root.get("disposed"), criteria.getDisposed()));
        }

        if (Objects.nonNull(criteria.getStartDate())) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createDate"), criteria.getStartDate()));
        }

        if (Objects.nonNull(criteria.getEndDate())) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createDate"), criteria.getEndDate()));
        }

        if (Objects.nonNull(criteria.getOrderedFrom()) || Objects.nonNull(criteria.getOrderedTo())) {
            // When it was ordered, else when it was created: the date the report's column shows.
            Expression<java.time.LocalDateTime> placed = criteriaBuilder.coalesce(
                    root.<java.time.LocalDateTime>get("orderDate"), root.<java.time.LocalDateTime>get("createDate"));
            if (Objects.nonNull(criteria.getOrderedFrom())) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(placed, criteria.getOrderedFrom()));
            }
            if (Objects.nonNull(criteria.getOrderedTo())) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(placed, criteria.getOrderedTo()));
            }
        }

        if (Objects.nonNull(criteria.getAssetTypeId())) {
            /*
             * An EXISTS over the order's lines, not a join: joining the lines would repeat an order
             * once per matching line and inflate the count that drives the pager. LEFT joins inside,
             * since a commodity's category is nullable (trap #1).
             */
            Subquery<Long> lines = query.subquery(Long.class);
            Root<pride.bank.erp.stockCommodity.StockCommodity> line =
                    lines.from(pride.bank.erp.stockCommodity.StockCommodity.class);
            Join<?, ?> category = line.join("commodity", JoinType.LEFT).join("assetType", JoinType.LEFT);
            lines.select(line.get("id")).where(
                    criteriaBuilder.equal(line.get("stock"), root),
                    criteriaBuilder.equal(category.get("id"), criteria.getAssetTypeId()));
            predicates.add(criteriaBuilder.exists(lines));
        }

        return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * The requested order, then {@code id} as a tie-break (trap #3): sorting on a non-unique column
     * alone lets MySQL order ties differently per query, so a page repeats one order and drops another.
     */
    private void setOrder(StockPage stockPage, CriteriaQuery<Stock> query, Root<Stock> root) {
        boolean ascending = sortDirection(stockPage).equals(Sort.Direction.ASC);
        String field = sortBy(stockPage);
        List<Order> orders = new ArrayList<>();
        orders.add(ascending ? criteriaBuilder.asc(root.get(field)) : criteriaBuilder.desc(root.get(field)));
        if (!"id".equals(field)) {
            orders.add(ascending ? criteriaBuilder.asc(root.get("id")) : criteriaBuilder.desc(root.get("id")));
        }
        query.orderBy(orders);
    }

    private Pageable getPageable(StockPage stockPage) {
        Sort sort = Sort.by(sortDirection(stockPage), sortBy(stockPage));
        return PageRequest.of(pageNumber(stockPage), pageSize(stockPage), sort);
    }

    private long getStocksCount(StockSearchCriteria criteria) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Stock> countRoot = countQuery.from(Stock.class);

        Predicate countPredicate = getPredicate(criteria, countRoot, countQuery);
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
