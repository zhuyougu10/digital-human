package com.medical.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.security.util.SecurityUtil;
import com.medical.user.domain.dto.UserCreateDTO;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 管理员 - 分页查询用户列表 */
    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @GetMapping("/list")
    public R<PageResult<UserVO>> list(PageQuery pageQuery,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(sysUserService.listUsers(pageQuery, keyword));
    }

    /** 管理员 - 创建用户 */
    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PostMapping("/add")
    public R<UserVO> add(@RequestBody @Valid UserCreateDTO dto) {
        return R.ok(sysUserService.createUser(dto));
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/info")
    public R<UserVO> getCurrentUser() {
        return R.ok(sysUserService.getUserById(SecurityUtil.getUserId()));
    }

    /** 更新当前用户信息 */
    @PutMapping("/info")
    public R<Void> updateCurrentUser(@RequestBody UserUpdateDTO dto) {
        sysUserService.updateUser(SecurityUtil.getUserId(), dto);
        return R.ok();
    }

    /** 管理员 - 禁用/启用用户 */
    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PutMapping("/{userId}/toggle-status")
    public R<Void> toggleStatus(@PathVariable Long userId) {
        sysUserService.toggleUserStatus(userId);
        return R.ok();
    }

    /** 管理员 - 分配角色 */
    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @PostMapping("/{userId}/role/{roleKey}")
    public R<Void> assignRole(@PathVariable Long userId, @PathVariable String roleKey) {
        sysUserService.assignRole(userId, roleKey);
        return R.ok();
    }

    /** 管理员 - 移除角色 */
    @SaCheckRole(UserConstants.ROLE_ADMIN)
    @DeleteMapping("/{userId}/role/{roleKey}")
    public R<Void> removeRole(@PathVariable Long userId, @PathVariable String roleKey) {
        sysUserService.removeRole(userId, roleKey);
        return R.ok();
    }

    /** 内部调用 - 根据ID获取用户信息 */
    @GetMapping("/inner/{userId}")
    public R<UserInfoDTO> getInnerUser(@PathVariable Long userId) {
        UserVO vo = sysUserService.getUserById(userId);
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(vo.getId());
        dto.setUsername(vo.getUsername());
        dto.setNickname(vo.getNickname());
        dto.setAvatar(vo.getAvatar());
        dto.setPhone(vo.getPhone());
        dto.setRoles(vo.getRoles());
        return R.ok(dto);
    }
}
