package com.medical.doctor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medical.common.core.constant.UserConstants;
import com.medical.common.redis.util.RedisUtil;
import com.medical.doctor.constant.DoctorCacheConstants;
import com.medical.doctor.domain.dto.DepartmentDTO;
import com.medical.doctor.domain.entity.Department;
import com.medical.doctor.domain.vo.DepartmentVO;
import com.medical.doctor.mapper.DepartmentMapper;
import com.medical.doctor.service.impl.DepartmentServiceImpl;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    @Test
    void list_shouldCacheOnlyWhenKeywordIsBlank() {
        Department department = new Department();
        department.setId(1L);
        department.setName("Internal Medicine");

        when(redisUtil.get(DoctorCacheConstants.DEPARTMENT_LIST_KEY)).thenReturn(null);
        when(departmentMapper.selectList(any())).thenReturn(List.of(department), List.of(department));

        List<DepartmentVO> blankKeywordResult = departmentService.list("   ");
        List<DepartmentVO> keywordResult = departmentService.list("internal");

        assertEquals(1L, blankKeywordResult.get(0).getId());
        assertEquals(1L, keywordResult.get(0).getId());
        verify(redisUtil, times(1)).set(
                eq(DoctorCacheConstants.DEPARTMENT_LIST_KEY),
                any(),
                eq(DoctorCacheConstants.DEPARTMENT_LIST_TTL_MINUTES),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void list_shouldReturnCachedDepartmentsWithoutDatabaseFallback() {
        DepartmentVO cachedDepartment = new DepartmentVO();
        cachedDepartment.setId(2L);
        cachedDepartment.setName("Surgery");

        when(redisUtil.get(DoctorCacheConstants.DEPARTMENT_LIST_KEY)).thenReturn(List.of(cachedDepartment));

        List<DepartmentVO> result = departmentService.list(null);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        verify(departmentMapper, never()).selectList(any());
        verify(redisUtil, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void create_shouldDeleteDepartmentListSingleCacheKey() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Pediatrics");

        departmentService.create(dto);

        verify(departmentMapper).insert(any(Department.class));
        verify(redisUtil).delete(DoctorCacheConstants.DEPARTMENT_LIST_KEY);
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void update_shouldIncrementDoctorListVersionAfterUpdatingDepartment() {
        Department department = new Department();
        department.setId(6L);
        department.setName("Old Name");

        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Neurology");

        when(departmentMapper.selectById(6L)).thenReturn(department);

        departmentService.update(6L, dto);

        verify(departmentMapper).updateById(department);
        verify(redisUtil).delete(DoctorCacheConstants.DEPARTMENT_LIST_KEY);
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void delete_shouldIncrementDoctorListVersionAfterDeletingDepartment() {
        Department department = new Department();
        department.setId(7L);

        when(departmentMapper.selectById(7L)).thenReturn(department);

        departmentService.delete(7L);

        verify(departmentMapper).deleteById(7L);
        verify(redisUtil).delete(DoctorCacheConstants.DEPARTMENT_LIST_KEY);
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void toggleStatus_shouldIncrementDoctorListVersionAfterChangingDepartmentStatus() {
        Department department = new Department();
        department.setId(8L);
        department.setStatus(UserConstants.STATUS_NORMAL);

        when(departmentMapper.selectById(8L)).thenReturn(department);

        departmentService.toggleStatus(8L);

        assertEquals(UserConstants.STATUS_DISABLED, department.getStatus());
        verify(departmentMapper).updateById(department);
        verify(redisUtil).delete(DoctorCacheConstants.DEPARTMENT_LIST_KEY);
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }
}
