/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.department.Department;
import pride.bank.erp.department.DepartmentRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentSeederTest {
    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private DepartmentSeeder departmentSeeder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedDepartmentsCreatesMissingDepartmentsUnderHeadOffice() {
        departmentSeeder = new DepartmentSeeder(departmentRepository, branchRepository, objectMapper);
        List<DepartmentSeedNode> departments = List.of(
                new DepartmentSeedNode("P&C", "People & Culture", "0")
        );

        Branch headOffice = Branch.builder().id(1L).shortCode("0").name("Head Office").build();

        when(branchRepository.findByShortCode("0")).thenReturn(Optional.of(headOffice));
        when(departmentRepository.findByShortCode("P&C")).thenReturn(Optional.empty());
        when(departmentRepository.findByNameIgnoreCase("People & Culture")).thenReturn(Optional.empty());
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        departmentSeeder.seedDepartments(departments);

        ArgumentCaptor<Department> departmentCaptor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository, atLeastOnce()).save(departmentCaptor.capture());

        Department savedDepartment = departmentCaptor.getValue();
        assertEquals("P&C", savedDepartment.getShortCode());
        assertEquals("People & Culture", savedDepartment.getName());
        assertSame(headOffice, savedDepartment.getBranch());
        assertNull(savedDepartment.getHeadOfDepartment());
        assertNull(savedDepartment.getManagersGroupEmail());
    }

    @Test
    void seedDepartmentsFailsWhenReferencedBranchDoesNotExist() {
        departmentSeeder = new DepartmentSeeder(departmentRepository, branchRepository, objectMapper);
        List<DepartmentSeedNode> departments = List.of(
                new DepartmentSeedNode("P&C", "People & Culture", "0")
        );

        when(branchRepository.findByShortCode("0")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> departmentSeeder.seedDepartments(departments));

        assertEquals("Branch with shortCode '0' referenced by department 'People & Culture' was not found", exception.getMessage());
    }

    @Test
    void seedDepartmentsUpdatesExistingDepartmentWithoutDuplicating() {
        departmentSeeder = new DepartmentSeeder(departmentRepository, branchRepository, objectMapper);
        List<DepartmentSeedNode> departments = List.of(
                new DepartmentSeedNode("P&C", "People & Culture", "0")
        );

        Branch headOffice = Branch.builder().id(1L).shortCode("0").name("Head Office").build();
        Department existingDepartment = Department.builder()
                .id(3L)
                .shortCode("P&C")
                .name("Old Name")
                .build();

        when(branchRepository.findByShortCode("0")).thenReturn(Optional.of(headOffice));
        when(departmentRepository.findByShortCode(eq("P&C"))).thenReturn(Optional.of(existingDepartment));
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));

        departmentSeeder.seedDepartments(departments);

        assertEquals("People & Culture", existingDepartment.getName());
        assertSame(headOffice, existingDepartment.getBranch());
    }
}