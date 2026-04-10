package com.medical.doctor.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.medical.api.doctor.dto.DoctorInfoDTO;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.security.util.SecurityUtil;
import com.medical.doctor.domain.dto.DoctorProfileDTO;
import com.medical.doctor.domain.vo.DoctorVO;
import com.medical.doctor.service.DoctorProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doctor")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorProfileService doctorProfileService;

    @GetMapping("/list")
    public R<PageResult<DoctorVO>> list(@RequestParam(required = false) Long departmentId,
                                        @RequestParam(required = false) String keyword,
                                        PageQuery pageQuery) {
        return R.ok(doctorProfileService.listByDepartment(departmentId, keyword, pageQuery));
    }

    @GetMapping("/{id}")
    public R<DoctorVO> getById(@PathVariable Long id) {
        return R.ok(doctorProfileService.getById(id));
    }

    @GetMapping("/search")
    public R<List<DoctorVO>> search(@RequestParam String keywords) {
        return R.ok(doctorProfileService.searchBySymptom(keywords));
    }

    @GetMapping("/my-profile")
    public R<DoctorVO> myProfile() {
        return R.ok(doctorProfileService.getByUserId(SecurityUtil.getUserId()));
    }

    @SaCheckRole(UserConstants.ROLE_DOCTOR)
    @PutMapping("/my-profile")
    public R<Void> updateMyProfile(@RequestBody DoctorProfileDTO dto) {
        doctorProfileService.updateMyProfile(SecurityUtil.getUserId(), dto);
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PostMapping
    public R<Void> create(@RequestBody @Valid DoctorProfileDTO dto) {
        doctorProfileService.create(dto);
        return R.ok();
    }

    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid DoctorProfileDTO dto) {
        doctorProfileService.update(id, dto);
        return R.ok();
    }

    @GetMapping("/inner/{doctorId}")
    public R<DoctorInfoDTO> getInnerById(@PathVariable Long doctorId) {
        DoctorVO vo = doctorProfileService.getById(doctorId);
        return R.ok(toDoctorInfoDTO(vo));
    }

    @GetMapping("/inner/by-user/{userId}")
    public R<DoctorInfoDTO> getInnerByUserId(@PathVariable Long userId) {
        DoctorVO vo = doctorProfileService.getByUserId(userId);
        return R.ok(toDoctorInfoDTO(vo));
    }

    private DoctorInfoDTO toDoctorInfoDTO(DoctorVO vo) {
        DoctorInfoDTO dto = new DoctorInfoDTO();
        dto.setId(vo.getId());
        dto.setUserId(vo.getUserId());
        dto.setName(vo.getName());
        dto.setTitle(vo.getTitle());
        dto.setAvatar(vo.getAvatar());
        dto.setSpecialties(vo.getSpecialties());
        dto.setConsultationFee(vo.getConsultationFee());
        dto.setDepartmentNames(vo.getDepartments() == null ? "" : vo.getDepartments()
                .stream()
                .map(d -> d.getName() == null ? "" : d.getName())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(",")));
        return dto;
    }

    @GetMapping("/inner/search")
    public R<List<DoctorInfoDTO>> innerSearch(@RequestParam String keywords) {
        List<DoctorInfoDTO> result = doctorProfileService.searchBySymptom(keywords).stream().map(vo -> {
            DoctorInfoDTO dto = new DoctorInfoDTO();
            dto.setId(vo.getId());
            dto.setUserId(vo.getUserId());
            dto.setName(vo.getName());
            dto.setTitle(vo.getTitle());
            dto.setAvatar(vo.getAvatar());
            dto.setSpecialties(vo.getSpecialties());
            dto.setConsultationFee(vo.getConsultationFee());
            dto.setDepartmentNames(vo.getDepartments() == null ? "" : vo.getDepartments()
                    .stream()
                    .map(d -> d.getName() == null ? "" : d.getName())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(",")));
            return dto;
        }).collect(Collectors.toList());
        return R.ok(result);
    }
}
