package com.medical.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.doctor.domain.dto.DepartmentDTO;
import com.medical.doctor.domain.entity.Department;
import com.medical.doctor.domain.vo.DepartmentVO;
import com.medical.doctor.mapper.DepartmentMapper;
import com.medical.doctor.service.DepartmentService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    @Override
    public List<DepartmentVO> list() {
        List<Department> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSort)
                        .orderByAsc(Department::getId)
        );
        return departments.stream().map(this::toDepartmentVO).collect(Collectors.toList());
    }

    @Override
    public DepartmentVO getById(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return toDepartmentVO(department);
    }

    @Override
    public void create(DepartmentDTO dto) {
        Department department = new Department();
        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
        department.setIcon(dto.getIcon());
        department.setSort(dto.getSort() == null ? 0 : dto.getSort());
        department.setStatus(UserConstants.STATUS_NORMAL);
        departmentMapper.insert(department);
    }

    @Override
    public void update(Long id, DepartmentDTO dto) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        if (dto.getName() != null) {
            department.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            department.setDescription(dto.getDescription());
        }
        if (dto.getIcon() != null) {
            department.setIcon(dto.getIcon());
        }
        if (dto.getSort() != null) {
            department.setSort(dto.getSort());
        }
        departmentMapper.updateById(department);
    }

    @Override
    public void delete(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        departmentMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        department.setStatus(department.getStatus() == UserConstants.STATUS_NORMAL
                ? UserConstants.STATUS_DISABLED : UserConstants.STATUS_NORMAL);
        departmentMapper.updateById(department);
    }

    private DepartmentVO toDepartmentVO(Department department) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(department.getId());
        vo.setName(department.getName());
        vo.setDescription(department.getDescription());
        vo.setIcon(department.getIcon());
        vo.setSort(department.getSort());
        vo.setStatus(department.getStatus());
        vo.setCreateTime(department.getCreateTime());
        return vo;
    }
}
