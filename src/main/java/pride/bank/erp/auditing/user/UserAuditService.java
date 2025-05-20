/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.auditing.user;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAuditService {
    @PersistenceContext
    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public Page<UserAuditResponse> getUserAuditDetails(Pageable pageable) {
        AuditReader auditReader = AuditReaderFactory.get(entityManager);

        AuditQuery query = auditReader.createQuery()
                .forRevisionsOfEntity(User.class, false, true)
                .addOrder(AuditEntity.revisionNumber().desc());

        @SuppressWarnings("unchecked")
        List<Object[]> auditRecords = (List<Object[]>) query.getResultList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), auditRecords.size());
        List<Object[]> paginatedRecords = auditRecords.subList(start, end);

        List<UserAuditResponse> response = new ArrayList<>();

        for (Object[] record : paginatedRecords) {
            User user = (User) record[0];
            DefaultRevisionEntity revisionEntity = (DefaultRevisionEntity) record[1];
            String revisionType = record[2].toString();
            Long createdById = user.getLastModifiedBy();

            User creator = null;
            if (createdById != null) {
                creator = userRepository.findById(createdById).orElse(null);
            }

            response.add(new UserAuditResponse(
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getStaffNumber(),
                    user.getGender(),
                    user.getProfileImage(),
                    revisionEntity.getId(),
                    revisionType,
                    user.getCreateDate(),
                    creator != null ? creator.getFirstName() + " " + creator.getLastName() : "Unknown",
                    user.getAvailability()
            ));
        }

        return new PageImpl<>(response, pageable, auditRecords.size());
    }
}
