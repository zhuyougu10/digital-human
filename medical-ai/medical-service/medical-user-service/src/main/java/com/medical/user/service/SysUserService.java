package com.medical.user.service;

import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.user.domain.dto.UserCreateDTO;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.vo.UserVO;

public interface SysUserService {
    /** 分页查询用户列表 */
    PageResult<UserVO> listUsers(PageQuery pageQuery, String keyword);
    /** 根据ID查询用户 */
    UserVO getUserById(Long userId);
    /** 更新用户信息 */
    void updateUser(Long userId, UserUpdateDTO dto);

    /** 创建用户 */
    UserVO createUser(UserCreateDTO dto);

    /** 禁用/启用用户 */
    void toggleUserStatus(Long userId);
    /** 分配角色 */
    void assignRole(Long userId, String roleKey);
    /** 移除角色 */
    void removeRole(Long userId, String roleKey);
}
