package com.medical.doctor.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.R;
import com.medical.doctor.domain.dto.DepartmentDTO;
import com.medical.doctor.domain.vo.DepartmentVO;
import com.medical.doctor.service.DepartmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping("/list")
    public R<List<DepartmentVO>> list() {
        return R.ok(departmentService.list());
    }

    @GetMapping("/{id}")
    public R<DepartmentVO> getById(@PathVariable Long id) {
        return R.ok(departmentService.getById(id));
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PostMapping
    public R<Void> create(@RequestBody DepartmentDTO dto) {
        departmentService.create(dto);
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody DepartmentDTO dto) {
        departmentService.update(id, dto);
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PutMapping("/{id}/toggle-status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        departmentService.toggleStatus(id);
        return R.ok();
    }
}
