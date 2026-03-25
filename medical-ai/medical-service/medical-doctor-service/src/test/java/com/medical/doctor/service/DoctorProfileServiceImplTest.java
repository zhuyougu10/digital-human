package com.medical.doctor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.redis.util.RedisUtil;
import com.medical.doctor.constant.DoctorCacheConstants;
import com.medical.doctor.domain.dto.DoctorProfileDTO;
import com.medical.doctor.domain.entity.Department;
import com.medical.doctor.domain.entity.DoctorDepartment;
import com.medical.doctor.domain.entity.DoctorProfile;
import com.medical.doctor.domain.vo.DoctorVO;
import com.medical.doctor.mapper.DepartmentMapper;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.service.impl.DoctorProfileServiceImpl;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoctorProfileServiceImplTest {

    @Mock
    private DoctorProfileMapper doctorProfileMapper;
    @Mock
    private DoctorDepartmentMapper doctorDepartmentMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private DoctorProfileServiceImpl doctorProfileService;

    @Test
    void getById_shouldReturnCachedDoctorDetailWithoutDatabaseFallback() {
        DoctorVO cachedDoctor = new DoctorVO();
        cachedDoctor.setId(8L);
        cachedDoctor.setName("Dr. Li");

        when(redisUtil.get(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "8")).thenReturn(cachedDoctor);

        DoctorVO result = doctorProfileService.getById(8L);

        assertEquals(8L, result.getId());
        verify(doctorProfileMapper, never()).selectById(any());
        verify(redisUtil, never()).set(any(), any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void listByDepartment_shouldReturnCachedPageWithoutDatabaseFallback() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(2);
        pageQuery.setPageSize(5);
        PageResult<DoctorVO> cachedPage = PageResult.of(List.of(new DoctorVO()), 1, 2, 5);

        when(redisUtil.get(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY)).thenReturn(0L);
        when(redisUtil.get("doctor:list:version:0:department:3:page:2:size:5:keyword:cardio")).thenReturn(cachedPage);

        PageResult<DoctorVO> result = doctorProfileService.listByDepartment(3L, "cardio", pageQuery);

        assertEquals(1, result.getRecords().size());
        verify(doctorDepartmentMapper, never()).selectList(any());
        verify(doctorProfileMapper, never()).selectPage(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void listByDepartment_shouldComposeCacheKeyFromDepartmentPageSizeAndKeyword() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(2);
        pageQuery.setPageSize(5);

        DoctorDepartment relation = new DoctorDepartment();
        relation.setDoctorId(11L);
        relation.setDepartmentId(3L);

        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(11L);
        doctor.setName("Dr. Chen");

        Department department = new Department();
        department.setId(3L);
        department.setName("Cardiology");

        Page<DoctorProfile> page = new Page<>(2, 5);
        page.setRecords(List.of(doctor));
        page.setTotal(1L);

        when(redisUtil.get(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY)).thenReturn(7L);
        when(redisUtil.get("doctor:list:version:7:department:3:page:2:size:5:keyword:heart")).thenReturn(null);
        when(doctorDepartmentMapper.selectList(any())).thenReturn(List.of(relation), List.of(relation));
        when(doctorProfileMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(departmentMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(department));

        PageResult<DoctorVO> result = doctorProfileService.listByDepartment(3L, "  heart  ", pageQuery);

        assertEquals(1, result.getRecords().size());
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisUtil).set(keyCaptor.capture(), any(PageResult.class), ttlCaptor.capture(), eq(TimeUnit.SECONDS));
        assertEquals("doctor:list:version:7:department:3:page:2:size:5:keyword:heart", keyCaptor.getValue());
        long baseSeconds = TimeUnit.MINUTES.toSeconds(DoctorCacheConstants.DOCTOR_LIST_TTL_MINUTES);
        assertTrue(ttlCaptor.getValue() >= baseSeconds);
        assertTrue(ttlCaptor.getValue() <= baseSeconds + DoctorCacheConstants.CACHE_TTL_JITTER_SECONDS);
    }

    @Test
    void getById_shouldWriteDetailCacheWithJitteredTtl() {
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(8L);
        doctor.setName("Dr. Li");

        when(redisUtil.get(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "8")).thenReturn(null);
        when(doctorProfileMapper.selectById(8L)).thenReturn(doctor);
        when(doctorDepartmentMapper.selectList(any())).thenReturn(List.of());

        DoctorVO result = doctorProfileService.getById(8L);

        assertEquals(8L, result.getId());
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisUtil).set(eq(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "8"), any(DoctorVO.class),
                ttlCaptor.capture(), eq(TimeUnit.SECONDS));
        long baseSeconds = TimeUnit.MINUTES.toSeconds(DoctorCacheConstants.DOCTOR_DETAIL_TTL_MINUTES);
        assertTrue(ttlCaptor.getValue() >= baseSeconds);
        assertTrue(ttlCaptor.getValue() <= baseSeconds + DoctorCacheConstants.CACHE_TTL_JITTER_SECONDS);
    }

    @Test
    void update_shouldIncrementDoctorListVersionAfterUpdatingDoctor() {
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(9L);

        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setName("Updated");

        when(doctorProfileMapper.selectById(9L)).thenReturn(doctor);

        doctorProfileService.update(9L, dto);

        verify(redisUtil).delete(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "9");
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void create_shouldIncrementDoctorListVersionAfterCreatingDoctor() {
        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setUserId(12L);
        dto.setName("Dr. Wang");

        doctorProfileService.create(dto);

        verify(doctorProfileMapper).insert(any(DoctorProfile.class));
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void updateMyProfile_shouldIncrementDoctorListVersionAfterUpdatingDoctor() {
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(15L);
        doctor.setUserId(101L);

        DoctorProfileDTO dto = new DoctorProfileDTO();
        dto.setName("Dr. Zhao");

        when(doctorProfileMapper.selectOne(any())).thenReturn(doctor);

        doctorProfileService.updateMyProfile(101L, dto);

        verify(doctorProfileMapper).updateById(doctor);
        verify(redisUtil).delete(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "15");
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }

    @Test
    void delete_shouldIncrementDoctorListVersionAfterDeletingDoctor() {
        DoctorProfile doctor = new DoctorProfile();
        doctor.setId(18L);

        when(doctorProfileMapper.selectById(18L)).thenReturn(doctor);

        doctorProfileService.delete(18L);

        verify(doctorProfileMapper).deleteById(18L);
        verify(doctorDepartmentMapper).delete(any());
        verify(redisUtil).delete(DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + "18");
        verify(redisUtil).increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }
}
