/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DepartmentSeeder {
    private static final String DEPARTMENTS_RESOURCE = "departments.json";

    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void seedDepartments() {
        try {
            ClassPathResource resource = new ClassPathResource(DEPARTMENTS_RESOURCE);
            List<DepartmentSeedNode> departments = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });

            seedDepartments(departments);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to seed departments from " + DEPARTMENTS_RESOURCE, exception);
        }
    }

    void seedDepartments(List<DepartmentSeedNode> departments) {
        for (DepartmentSeedNode departmentNode : departments) {
            Branch branch = resolveBranch(departmentNode);
            findOrCreateDepartment(departmentNode, branch);
        }
    }

    private Branch resolveBranch(DepartmentSeedNode node) {
        if (node.getBranchShortCode() == null || node.getBranchShortCode().isBlank()) {
            throw new IllegalStateException("Department '" + node.getName() + "' is missing branchShortCode in " + DEPARTMENTS_RESOURCE);
        }

        return branchRepository.findByShortCode(node.getBranchShortCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Branch with shortCode '" + node.getBranchShortCode() + "' referenced by department '" + node.getName() + "' was not found"
                ));
    }

    private Department findOrCreateDepartment(DepartmentSeedNode node, Branch branch) {
        Optional<Department> existingDepartment = findExistingDepartment(node);

        if (existingDepartment.isPresent()) {
            Department department = existingDepartment.get();
            boolean requiresUpdate = false;

            if (!Objects.equals(department.getName(), node.getName())) {
                department.setName(node.getName());
                requiresUpdate = true;
            }

            if (!Objects.equals(department.getShortCode(), node.getShortCode())) {
                department.setShortCode(node.getShortCode());
                requiresUpdate = true;
            }

            if (department.getBranch() == null || !Objects.equals(department.getBranch().getId(), branch.getId())) {
                department.setBranch(branch);
                requiresUpdate = true;
            }

            if (requiresUpdate) {
                return departmentRepository.save(department);
            }

            return department;
        }

        return departmentRepository.save(
                Department.builder()
                        .name(node.getName())
                        .shortCode(node.getShortCode())
                        .branch(branch)
                        .headOfDepartment(null)
                        .managersGroupEmail(null)
                        .build()
        );
    }

    private Optional<Department> findExistingDepartment(DepartmentSeedNode node) {
        if (node.getShortCode() != null && !node.getShortCode().isBlank()) {
            Optional<Department> departmentByShortCode = departmentRepository.findByShortCode(node.getShortCode());
            if (departmentByShortCode.isPresent()) {
                return departmentByShortCode;
            }
        }

        if (node.getName() != null && !node.getName().isBlank()) {
            return departmentRepository.findByNameIgnoreCase(node.getName());
        }

        return Optional.empty();
    }
}