package com.medical.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.security.util.SecurityUtil;
import com.medical.doctor.domain.dto.DoctorProfileDTO;
import com.medical.doctor.domain.entity.Department;
import com.medical.doctor.domain.entity.DoctorDepartment;
import com.medical.doctor.domain.entity.DoctorProfile;
import com.medical.doctor.domain.vo.DepartmentVO;
import com.medical.doctor.domain.vo.DoctorVO;
import com.medical.doctor.mapper.DepartmentMapper;
import com.medical.doctor.mapper.DoctorDepartmentMapper;
import com.medical.doctor.mapper.DoctorProfileMapper;
import com.medical.doctor.service.DoctorProfileService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DoctorProfileServiceImpl implements DoctorProfileService {

    private final DoctorProfileMapper doctorProfileMapper;
    private final DoctorDepartmentMapper doctorDepartmentMapper;
    private final DepartmentMapper departmentMapper;

    @Override
    public PageResult<DoctorVO> listByDepartment(Long departmentId, String keyword, PageQuery pageQuery) {
        List<Long> doctorIds = null;
        if (departmentId != null) {
            doctorIds = doctorDepartmentMapper.selectList(
                            new LambdaQueryWrapper<DoctorDepartment>()
                                    .eq(DoctorDepartment::getDepartmentId, departmentId))
                    .stream()
                    .map(DoctorDepartment::getDoctorId)
                    .distinct()
                    .collect(Collectors.toList());
            if (doctorIds.isEmpty()) {
                return PageResult.of(Collections.emptyList(), 0,
                        pageQuery.getPageNum(), pageQuery.getPageSize());
            }
        }

        Page<DoctorProfile> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<DoctorProfile> wrapper = new LambdaQueryWrapper<>();
        if (doctorIds != null) {
            wrapper.in(DoctorProfile::getId, doctorIds);
        }
        wrapper.like(StringUtils.hasText(keyword), DoctorProfile::getName, keyword);
        wrapper.orderByDesc(DoctorProfile::getCreateTime);
        Page<DoctorProfile> result = doctorProfileMapper.selectPage(page, wrapper);
        Map<Long, List<DepartmentVO>> departmentMap = buildDepartmentMap(result.getRecords());
        List<DoctorVO> records = result.getRecords().stream()
                .map(profile -> buildDoctorVO(profile, departmentMap))
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public List<DoctorVO> searchBySymptom(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] keywordArray = keywords.split("[,，]");
        LambdaQueryWrapper<DoctorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            boolean hasKeyword = false;
            for (String item : keywordArray) {
                String keyword = item.trim();
                if (keyword.isEmpty()) {
                    continue;
                }
                hasKeyword = true;
                w.or(c -> c.like(DoctorProfile::getSpecialties, keyword)
                        .or()
                        .like(DoctorProfile::getTreatmentAreas, keyword));
            }
            if (!hasKeyword) {
                w.apply("1=0");
            }
        });
        wrapper.orderByDesc(DoctorProfile::getCreateTime);

        List<DoctorProfile> profiles = doctorProfileMapper.selectList(wrapper);
        Map<Long, List<DepartmentVO>> departmentMap = buildDepartmentMap(profiles);
        return profiles
                .stream()
                .map(profile -> buildDoctorVO(profile, departmentMap))
                .collect(Collectors.toList());
    }

    @Override
    public DoctorVO getById(Long id) {
        DoctorProfile profile = doctorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return buildDoctorVO(profile);
    }

    @Override
    public DoctorVO getByUserId(Long userId) {
        DoctorProfile profile = doctorProfileMapper.selectOne(
                new LambdaQueryWrapper<DoctorProfile>()
                        .eq(DoctorProfile::getUserId, userId)
                        .last("limit 1"));
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return buildDoctorVO(profile);
    }

    @Override
    @Transactional
    public void create(DoctorProfileDTO dto) {
        DoctorProfile profile = new DoctorProfile();
        profile.setUserId(dto.getUserId() != null ? dto.getUserId() : SecurityUtil.getUserId());
        profile.setName(dto.getName());
        profile.setTitle(dto.getTitle());
        profile.setIntroduction(dto.getIntroduction());
        profile.setSpecialties(dto.getSpecialties());
        profile.setTreatmentAreas(dto.getTreatmentAreas());
        profile.setConsultationFee(dto.getConsultationFee());
        profile.setStatus(0);
        doctorProfileMapper.insert(profile);
        saveDoctorDepartments(profile.getId(), dto.getDepartmentIds());
    }

    @Override
    @Transactional
    public void update(Long id, DoctorProfileDTO dto) {
        DoctorProfile profile = doctorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        fillDoctorProfile(profile, dto);
        doctorProfileMapper.updateById(profile);
        resetDoctorDepartments(id, dto.getDepartmentIds());
    }

    @Override
    @Transactional
    public void updateMyProfile(Long userId, DoctorProfileDTO dto) {
        DoctorProfile profile = doctorProfileMapper.selectOne(
                new LambdaQueryWrapper<DoctorProfile>()
                        .eq(DoctorProfile::getUserId, userId)
                        .last("limit 1"));
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        fillDoctorProfile(profile, dto);
        doctorProfileMapper.updateById(profile);
        if (dto.getDepartmentIds() != null) {
            resetDoctorDepartments(profile.getId(), dto.getDepartmentIds());
        }
    }

    @Override
    public void delete(Long id) {
        DoctorProfile profile = doctorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        doctorProfileMapper.deleteById(id);
        doctorDepartmentMapper.delete(new LambdaQueryWrapper<DoctorDepartment>()
                .eq(DoctorDepartment::getDoctorId, id));
    }

    private void fillDoctorProfile(DoctorProfile profile, DoctorProfileDTO dto) {
        if (dto.getName() != null) {
            profile.setName(dto.getName());
        }
        if (dto.getTitle() != null) {
            profile.setTitle(dto.getTitle());
        }
        if (dto.getIntroduction() != null) {
            profile.setIntroduction(dto.getIntroduction());
        }
        if (dto.getSpecialties() != null) {
            profile.setSpecialties(dto.getSpecialties());
        }
        if (dto.getTreatmentAreas() != null) {
            profile.setTreatmentAreas(dto.getTreatmentAreas());
        }
        if (dto.getConsultationFee() != null) {
            profile.setConsultationFee(dto.getConsultationFee());
        }
    }

    private void saveDoctorDepartments(Long doctorId, List<Long> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(departmentIds);
        for (Long departmentId : uniqueIds) {
            Department department = departmentMapper.selectById(departmentId);
            if (department == null) {
                throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
            }
            DoctorDepartment relation = new DoctorDepartment();
            relation.setDoctorId(doctorId);
            relation.setDepartmentId(departmentId);
            doctorDepartmentMapper.insert(relation);
        }
    }

    private void resetDoctorDepartments(Long doctorId, List<Long> departmentIds) {
        doctorDepartmentMapper.delete(new LambdaQueryWrapper<DoctorDepartment>()
                .eq(DoctorDepartment::getDoctorId, doctorId));
        saveDoctorDepartments(doctorId, departmentIds);
    }

    private DoctorVO buildDoctorVO(DoctorProfile profile) {
        return buildDoctorVO(profile, buildDepartmentMap(List.of(profile)));
    }

    private DoctorVO buildDoctorVO(DoctorProfile profile, Map<Long, List<DepartmentVO>> departmentMap) {
        DoctorVO vo = new DoctorVO();
        vo.setId(profile.getId());
        vo.setUserId(profile.getUserId());
        vo.setName(profile.getName());
        vo.setTitle(profile.getTitle());
        vo.setAvatar(profile.getAvatar());
        vo.setIntroduction(profile.getIntroduction());
        vo.setSpecialties(profile.getSpecialties());
        vo.setTreatmentAreas(profile.getTreatmentAreas());
        vo.setConsultationFee(profile.getConsultationFee());
        vo.setStatus(profile.getStatus());
        vo.setDepartments(departmentMap.getOrDefault(profile.getId(), Collections.emptyList()));
        return vo;
    }

    private Map<Long, List<DepartmentVO>> buildDepartmentMap(List<DoctorProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> doctorIds = profiles.stream()
                .map(DoctorProfile::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (doctorIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DoctorDepartment> relations = doctorDepartmentMapper.selectList(
                new LambdaQueryWrapper<DoctorDepartment>().in(DoctorDepartment::getDoctorId, doctorIds));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> departmentIds = relations.stream()
                .map(DoctorDepartment::getDepartmentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, Department> departmentMap = departmentMapper.selectBatchIds(departmentIds)
                .stream()
                .collect(Collectors.toMap(Department::getId, department -> department));

        Map<Long, List<DepartmentVO>> doctorDepartmentMap = new HashMap<>();
        for (DoctorDepartment relation : relations) {
            Department department = departmentMap.get(relation.getDepartmentId());
            if (department == null) {
                continue;
            }
            doctorDepartmentMap.computeIfAbsent(relation.getDoctorId(), key -> new ArrayList<>())
                    .add(toDepartmentVO(department));
        }
        return doctorDepartmentMap;
    }

    private DepartmentVO toDepartmentVO(Department department) {
        DepartmentVO departmentVO = new DepartmentVO();
        departmentVO.setId(department.getId());
        departmentVO.setName(department.getName());
        departmentVO.setDescription(department.getDescription());
        departmentVO.setIcon(department.getIcon());
        departmentVO.setSort(department.getSort());
        departmentVO.setStatus(department.getStatus());
        departmentVO.setCreateTime(department.getCreateTime());
        return departmentVO;
    }
}
