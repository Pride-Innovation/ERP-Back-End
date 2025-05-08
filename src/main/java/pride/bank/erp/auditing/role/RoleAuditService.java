/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.role;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.role.Role;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleAuditService {

    @PersistenceContext
    private final EntityManager entityManager;
    @Autowired
    private UserRepository userRepository;

    public Page<RoleAuditResponse> getRoleAuditDetails(Pageable pageable) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(Role.class, false, true)
                .addOrder(AuditEntity.revisionNumber().desc());

        @SuppressWarnings("unchecked")
        List<Object[]> auditRecords = (List<Object[]>) query.getResultList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), auditRecords.size());
        List<Object[]> paginatedRecords = auditRecords.subList(start, end);

        List<RoleAuditResponse> response = new ArrayList<>();

        for (Object[] record : paginatedRecords) {
            Role role = (Role) record[0];
            DefaultRevisionEntity revisionEntity = (DefaultRevisionEntity) record[1];
            String revisionType = record[2].toString();
            Long createdById = role.getCreatedBy();

            User user = userRepository.findById(createdById).orElse(null);

            response.add(new RoleAuditResponse(
                    role.getName(),
                    revisionEntity.getId(),
                    revisionType,
                    role.getCreateDate(),
                    user != null ? user.getFirstName() + " " + user.getLastName() : "Unknown"
            ));
        }

        return new PageImpl<>(response, pageable, auditRecords.size());
    }
}