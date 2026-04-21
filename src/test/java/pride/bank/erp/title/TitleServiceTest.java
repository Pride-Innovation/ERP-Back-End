/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.title;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import pride.bank.erp.role.RoleService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TitleServiceTest {
    @Mock
    private TitleSearchDao titleSearchDao;

    @Mock
    private TitleRepository titleRepository;

    @Mock
    private RoleService roleService;

    @InjectMocks
    private TitleService titleService;

    @Test
    void fetchAllTitlesUsesPaginationAndAllFilters() {
        TitleQueryParams params = new TitleQueryParams();
        params.setPageNumber(2);
        params.setPageSize(5);
        params.setSortBy("name");
        params.setSortDirection("DESC");
        params.setName("head");
        params.setShortCode("HC");
        params.setReportsToId(11L);
        params.setRoleId(7L);

        Page<Title> expectedPage = new PageImpl<>(List.of());
        when(titleSearchDao.findAllWithFilters(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(expectedPage);

        Page<Title> response = titleService.fetchAllTitles(params);

        ArgumentCaptor<TitlePage> pageCaptor = ArgumentCaptor.forClass(TitlePage.class);
        ArgumentCaptor<TitleSearchCriteria> criteriaCaptor = ArgumentCaptor.forClass(TitleSearchCriteria.class);
        verify(titleSearchDao).findAllWithFilters(pageCaptor.capture(), criteriaCaptor.capture());

        TitlePage capturedPage = pageCaptor.getValue();
        TitleSearchCriteria capturedCriteria = criteriaCaptor.getValue();

        assertSame(expectedPage, response);
        assertEquals(2, capturedPage.getPageNumber());
        assertEquals(5, capturedPage.getPageSize());
        assertEquals("name", capturedPage.getSortBy());
        assertEquals(Sort.Direction.DESC, capturedPage.getSortDirection());
        assertEquals("head", capturedCriteria.getName());
        assertEquals("HC", capturedCriteria.getShortCode());
        assertEquals(11L, capturedCriteria.getReportsToId());
        assertEquals(7L, capturedCriteria.getRoleId());
    }
}