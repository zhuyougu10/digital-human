package com.medical.doctor.service;

import com.medical.doctor.domain.dto.DepartmentDTO;
import com.medical.doctor.domain.vo.DepartmentVO;
import java.util.List;

public interface DepartmentService {
    List<DepartmentVO> list();

    DepartmentVO getById(Long id);

    void create(DepartmentDTO dto);

    void update(Long id, DepartmentDTO dto);

    void delete(Long id);

    void toggleStatus(Long id);
}
