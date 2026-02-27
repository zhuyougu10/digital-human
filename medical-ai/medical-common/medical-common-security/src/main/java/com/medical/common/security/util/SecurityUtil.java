package com.medical.common.security.util;

import cn.dev33.satoken.stp.StpUtil;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;

public class SecurityUtil {

    /** 获取当前登录用户ID */
    public static Long getUserId() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 获取当前登录用户角色列表 */
    public static java.util.List<String> getRoles() {
        return StpUtil.getRoleList();
    }

    /** 检查当前用户是否具有指定角色 */
    public static boolean hasRole(String role) {
        return StpUtil.hasRole(role);
    }

    /** 判断是否已登录 */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /** 当前用户退出登录 */
    public static void logout() {
        StpUtil.logout();
    }
}
