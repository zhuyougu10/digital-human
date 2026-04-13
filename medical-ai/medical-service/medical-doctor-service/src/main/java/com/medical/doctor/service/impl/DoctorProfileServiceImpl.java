package com.medical.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.redis.util.RedisUtil;
import com.medical.common.security.util.SecurityUtil;
import com.medical.doctor.constant.DoctorCacheConstants;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DoctorProfileServiceImpl implements DoctorProfileService {

    private static final Map<String, List<String>> SYMPTOM_DEPARTMENT_MAPPING = Map.ofEntries(
            Map.entry("发烧", List.of("内科", "儿科")),
            Map.entry("发热", List.of("内科", "儿科")),
            Map.entry("咳嗽", List.of("内科", "儿科")),
            Map.entry("流鼻涕", List.of("内科", "儿科", "耳鼻喉科")),
            Map.entry("鼻塞", List.of("耳鼻喉科", "内科")),
            Map.entry("喉咙痛", List.of("耳鼻喉科", "内科")),
            Map.entry("咽喉痛", List.of("耳鼻喉科", "内科")),
            Map.entry("嗓子痛", List.of("耳鼻喉科", "内科")),
            Map.entry("头痛", List.of("神经内科", "内科")),
            Map.entry("头疼", List.of("神经内科", "内科")),
            Map.entry("头晕", List.of("神经内科", "内科")),
            Map.entry("腹痛", List.of("内科", "外科", "妇产科", "儿科")),
            Map.entry("肚子痛", List.of("内科", "外科", "妇产科", "儿科")),
            Map.entry("腹泻", List.of("内科", "儿科")),
            Map.entry("恶心", List.of("内科")),
            Map.entry("呕吐", List.of("内科", "儿科")),
            Map.entry("胸痛", List.of("内科", "外科")),
            Map.entry("皮疹", List.of("皮肤科")),
            Map.entry("过敏", List.of("皮肤科")),
            Map.entry("眼痛", List.of("眼科")),
            Map.entry("视力模糊", List.of("眼科")),
            Map.entry("牙痛", List.of("口腔科")),
            Map.entry("牙龈肿痛", List.of("口腔科")),
            Map.entry("月经不调", List.of("妇产科")),
            Map.entry("阴道出血", List.of("妇产科")),
            Map.entry("失眠", List.of("神经内科", "中医科")),
            Map.entry("腰痛", List.of("外科", "中医科")),
            Map.entry("外伤", List.of("外科"))
    );

    private final DoctorProfileMapper doctorProfileMapper;
    private final DoctorDepartmentMapper doctorDepartmentMapper;
    private final DepartmentMapper departmentMapper;
    private final RedisUtil redisUtil;

    @Override
    public PageResult<DoctorVO> listByDepartment(Long departmentId, String keyword, PageQuery pageQuery) {
        String cacheKey = buildDoctorListCacheKey(departmentId, keyword, pageQuery);
        PageResult<DoctorVO> cachedResult = getCachedDoctorList(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

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
                PageResult<DoctorVO> emptyResult = PageResult.of(Collections.emptyList(), 0,
                        pageQuery.getPageNum(), pageQuery.getPageSize());
                cacheDoctorList(cacheKey, emptyResult);
                return emptyResult;
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
        PageResult<DoctorVO> pageResult = PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
        cacheDoctorList(cacheKey, pageResult);
        return pageResult;
    }

    @Override
    public List<DoctorVO> searchBySymptom(String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalizedKeywords = normalizeKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<DoctorProfile> textMatchedProfiles = searchDoctorsByKeywordText(normalizedKeywords);
        List<DoctorProfile> departmentMatchedProfiles = searchDoctorsByMappedDepartments(normalizedKeywords);
        List<DoctorProfile> profiles = mergeProfiles(textMatchedProfiles, departmentMatchedProfiles);
        Map<Long, List<DepartmentVO>> departmentMap = buildDepartmentMap(profiles);
        return profiles
                .stream()
                .map(profile -> buildDoctorVO(profile, departmentMap))
                .collect(Collectors.toList());
    }

    @Override
    public DoctorVO getById(Long id) {
        DoctorVO cachedDoctor = getCachedDoctorDetail(id);
        if (cachedDoctor != null) {
            return cachedDoctor;
        }

        DoctorProfile profile = doctorProfileMapper.selectById(id);
        if (profile == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        DoctorVO result = buildDoctorVO(profile);
        cacheDoctorDetail(id, result);
        return result;
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
        redisUtil.increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
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
        invalidateDoctorCaches(id);
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
        invalidateDoctorCaches(profile.getId());
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
        invalidateDoctorCaches(id);
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

    private List<String> normalizeKeywords(String keywords) {
        String[] keywordArray = keywords.split("[,，]");
        List<String> normalizedKeywords = new ArrayList<>();
        for (String item : keywordArray) {
            String keyword = item == null ? "" : item.trim().replace(" ", "");
            if (keyword.isEmpty()) {
                continue;
            }
            normalizedKeywords.add(keyword);
        }
        return normalizedKeywords;
    }

    private List<DoctorProfile> searchDoctorsByKeywordText(List<String> normalizedKeywords) {
        LambdaQueryWrapper<DoctorProfile> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            boolean hasKeyword = false;
            for (String keyword : normalizedKeywords) {
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
        return doctorProfileMapper.selectList(wrapper);
    }

    private List<DoctorProfile> searchDoctorsByMappedDepartments(List<String> normalizedKeywords) {
        Set<String> departmentNames = resolveDepartmentNamesBySymptoms(normalizedKeywords);
        if (departmentNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<Department> departments = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .in(Department::getName, departmentNames)
                .eq(Department::getStatus, UserConstants.STATUS_NORMAL));
        if (departments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> departmentIds = departments.stream()
                .map(Department::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (departmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> doctorIds = doctorDepartmentMapper.selectList(new LambdaQueryWrapper<DoctorDepartment>()
                        .in(DoctorDepartment::getDepartmentId, departmentIds))
                .stream()
                .map(DoctorDepartment::getDoctorId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (doctorIds.isEmpty()) {
            return Collections.emptyList();
        }

        return doctorProfileMapper.selectBatchIds(doctorIds);
    }

    private Set<String> resolveDepartmentNamesBySymptoms(List<String> normalizedKeywords) {
        Set<String> departmentNames = new LinkedHashSet<>();
        for (String keyword : normalizedKeywords) {
            for (Map.Entry<String, List<String>> entry : SYMPTOM_DEPARTMENT_MAPPING.entrySet()) {
                String symptom = entry.getKey();
                if (keyword.contains(symptom) || symptom.contains(keyword)) {
                    departmentNames.addAll(entry.getValue());
                }
            }
        }
        return departmentNames;
    }

    private List<DoctorProfile> mergeProfiles(List<DoctorProfile> textMatchedProfiles, List<DoctorProfile> departmentMatchedProfiles) {
        Map<Long, DoctorProfile> mergedProfiles = new LinkedHashMap<>();
        addProfiles(mergedProfiles, departmentMatchedProfiles);
        addProfiles(mergedProfiles, textMatchedProfiles);
        return new ArrayList<>(mergedProfiles.values());
    }

    private void addProfiles(Map<Long, DoctorProfile> mergedProfiles, List<DoctorProfile> profiles) {
        if (CollectionUtils.isEmpty(profiles)) {
            return;
        }
        for (DoctorProfile profile : profiles) {
            if (profile == null || profile.getId() == null) {
                continue;
            }
            mergedProfiles.putIfAbsent(profile.getId(), profile);
        }
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

    @SuppressWarnings("unchecked")
    private PageResult<DoctorVO> getCachedDoctorList(String cacheKey) {
        return redisUtil.get(cacheKey);
    }

    @SuppressWarnings("unchecked")
    private DoctorVO getCachedDoctorDetail(Long doctorId) {
        return redisUtil.get(buildDoctorDetailCacheKey(doctorId));
    }

    private String buildDoctorDetailCacheKey(Long doctorId) {
        return DoctorCacheConstants.DOCTOR_DETAIL_KEY_PREFIX + doctorId;
    }

    private String buildDoctorListCacheKey(Long departmentId, String keyword, PageQuery pageQuery) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long version = getDoctorListVersion();
        return DoctorCacheConstants.DOCTOR_LIST_KEY_PREFIX
                + "version:" + version
                + ":department:" + departmentId
                + ":page:" + pageQuery.getPageNum()
                + ":size:" + pageQuery.getPageSize()
                + ":keyword:" + normalizedKeyword;
    }

    private void cacheDoctorList(String cacheKey, PageResult<DoctorVO> pageResult) {
        redisUtil.set(cacheKey, pageResult, buildJitteredDoctorTtlSeconds(DoctorCacheConstants.DOCTOR_LIST_TTL_MINUTES),
                TimeUnit.SECONDS);
    }

    private void cacheDoctorDetail(Long doctorId, DoctorVO doctor) {
        redisUtil.set(buildDoctorDetailCacheKey(doctorId), doctor,
                buildJitteredDoctorTtlSeconds(DoctorCacheConstants.DOCTOR_DETAIL_TTL_MINUTES), TimeUnit.SECONDS);
    }

    private long buildJitteredDoctorTtlSeconds(long baseMinutes) {
        return TimeUnit.MINUTES.toSeconds(baseMinutes)
                + ThreadLocalRandom.current().nextLong(DoctorCacheConstants.CACHE_TTL_JITTER_SECONDS + 1);
    }

    private long getDoctorListVersion() {
        Number version = redisUtil.get(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
        return version == null ? 0L : version.longValue();
    }

    private void invalidateDoctorCaches(Long doctorId) {
        redisUtil.delete(buildDoctorDetailCacheKey(doctorId));
        redisUtil.increment(DoctorCacheConstants.DOCTOR_LIST_VERSION_KEY);
    }
}
