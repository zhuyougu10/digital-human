package com.medical.doctor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
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

    @Test
    void searchBySymptom_shouldReturnDoctorsFromMappedDepartmentsWhenTextSearchMisses() {
        Department internalMedicine = new Department();
        internalMedicine.setId(1L);
        internalMedicine.setName("内科");
        internalMedicine.setStatus(0);

        DoctorDepartment relation = new DoctorDepartment();
        relation.setDoctorId(2L);
        relation.setDepartmentId(1L);

        DoctorProfile mappedDoctor = new DoctorProfile();
        mappedDoctor.setId(2L);
        mappedDoctor.setName("Dr. Internal");
        mappedDoctor.setSpecialties("高血压");

        when(doctorProfileMapper.selectList(any())).thenReturn(List.of());
        when(departmentMapper.selectList(argThat(wrapper -> wrapper != null))).thenReturn(List.of(internalMedicine));
        when(doctorDepartmentMapper.selectList(argThat(wrapper -> wrapper != null))).thenReturn(List.of(relation), List.of(relation));
        when(doctorProfileMapper.selectBatchIds(List.of(2L))).thenReturn(List.of(mappedDoctor));
        when(departmentMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(internalMedicine));

        List<DoctorVO> result = doctorProfileService.searchBySymptom("发烧,咳嗽");

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals("内科", result.get(0).getDepartments().get(0).getName());
    }

    @Test
    void searchBySymptom_shouldMergeDepartmentMatchesAndTextMatchesWithoutDuplicates() {
        Department entDepartment = new Department();
        entDepartment.setId(7L);
        entDepartment.setName("耳鼻喉科");
        entDepartment.setStatus(0);

        DoctorDepartment relation = new DoctorDepartment();
        relation.setDoctorId(8L);
        relation.setDepartmentId(7L);

        DoctorProfile matchedDoctor = new DoctorProfile();
        matchedDoctor.setId(8L);
        matchedDoctor.setName("Dr. Ent");
        matchedDoctor.setSpecialties("喉炎");

        when(doctorProfileMapper.selectList(any())).thenReturn(List.of(matchedDoctor));
        when(departmentMapper.selectList(any())).thenReturn(List.of(entDepartment));
        when(doctorDepartmentMapper.selectList(any())).thenReturn(List.of(relation), List.of(relation));
        when(doctorProfileMapper.selectBatchIds(List.of(8L))).thenReturn(List.of(matchedDoctor));
        when(departmentMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(entDepartment));

        List<DoctorVO> result = doctorProfileService.searchBySymptom("喉咙痛");

        assertEquals(1, result.size());
        assertEquals(8L, result.get(0).getId());
        assertEquals("耳鼻喉科", result.get(0).getDepartments().get(0).getName());
    }

    @Test
    void searchBySymptom_shouldFilterOutMappedDepartmentsMissingFromActiveDepartmentTable() {
        Department surgery = new Department();
        surgery.setId(2L);
        surgery.setName("外科");
        surgery.setStatus(0);

        when(doctorProfileMapper.selectList(any())).thenReturn(List.of());
        when(departmentMapper.selectList(any())).thenReturn(List.of(surgery), List.of());

        List<DoctorVO> result = doctorProfileService.searchBySymptom("发烧");

        assertTrue(result.isEmpty());
        verify(doctorDepartmentMapper, never()).selectList(any());
        verify(doctorProfileMapper, never()).selectBatchIds(any(Collection.class));
    }
}
