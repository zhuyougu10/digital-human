package com.medical.doctor.service;

import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.doctor.domain.dto.DoctorProfileDTO;
import com.medical.doctor.domain.vo.DoctorVO;
import java.util.List;

public interface DoctorProfileService {
    PageResult<DoctorVO> listByDepartment(Long departmentId, String keyword, PageQuery pageQuery);

    List<DoctorVO> searchBySymptom(String keywords);

    DoctorVO getById(Long id);

    DoctorVO getByUserId(Long userId);

    DoctorVO getByName(String name);

    void create(DoctorProfileDTO dto);

    void update(Long id, DoctorProfileDTO dto);

    void updateMyProfile(Long userId, DoctorProfileDTO dto);

    void delete(Long id);
}
