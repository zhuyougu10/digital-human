# 03 - 用户服务 (user-service)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现用户注册、账号密码登录、微信小程序登录、角色权限管理、用户 CRUD，为整个系统提供认证基础。

**Architecture:** 基于 Sa-Token 实现登录鉴权，微信登录通过 code2session 换取 openid 并绑定用户。服务注册到 Nacos，对外通过 Feign API 暴露接口。

**Tech Stack:** Spring Boot 3.3.x, Sa-Token, MyBatis-Plus, MySQL, Redis, WxJava (微信SDK)

**前置依赖:** `01-project-init.md` + `02-common-modules.md` 完成

---

## Task 1: 数据库表设计 - 用户相关表

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/resources/db/V1__init_user_tables.sql`

**Step 1: 编写 DDL**

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) DEFAULT NULL COMMENT '密码(BCrypt)',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `status` TINYINT DEFAULT 0 COMMENT '状态 0正常 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `create_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_key` VARCHAR(32) NOT NULL COMMENT '角色标识(PATIENT/DOCTOR/ADMIN)',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 0 COMMENT '状态 0正常 1禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 微信用户绑定表
CREATE TABLE IF NOT EXISTS `wx_user_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '系统用户ID',
    `openid` VARCHAR(64) NOT NULL COMMENT '微信OpenID',
    `unionid` VARCHAR(64) DEFAULT NULL COMMENT '微信UnionID',
    `session_key` VARCHAR(128) DEFAULT NULL COMMENT '会话密钥',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信用户绑定表';

-- 初始化角色数据
INSERT INTO `sys_role` (`role_key`, `role_name`, `sort`) VALUES
('ADMIN', '管理员', 1),
('DOCTOR', '医生', 2),
('PATIENT', '患者', 3);

-- 初始化管理员账号 (密码: admin123)
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`) VALUES
('admin', '$2a$10$VQECfGqK8MzRhLXnBz7G6eFNv.rXOqDZCFGJGRFEdAPqke4dBwxYi', '系统管理员', 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);
```

**Step 2: 在 Docker init.sql 中追加 source 或合并到 medical_user 库初始化**

---

## Task 2: Entity 实体类

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/entity/SysUser.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/entity/SysRole.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/entity/SysUserRole.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/entity/WxUserBinding.java`

**Step 1: SysUser 实体**

```java
package com.medical.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.medical.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
}
```

**Step 2: SysRole 实体**

```java
package com.medical.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleKey;
    private String roleName;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
```

**Step 3: SysUserRole 实体**

```java
package com.medical.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;
}
```

**Step 4: WxUserBinding 实体**

```java
package com.medical.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wx_user_binding")
public class WxUserBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String openid;
    private String unionid;
    private String sessionKey;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

---

## Task 3: Mapper 层

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/mapper/SysUserMapper.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/mapper/SysRoleMapper.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/mapper/SysUserRoleMapper.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/mapper/WxUserBindingMapper.java`

**Step 1: SysUserMapper**

```java
package com.medical.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.user.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_key FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);
}
```

**Step 2: 其他 Mapper（简单继承 BaseMapper）**

```java
// SysRoleMapper.java
package com.medical.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.user.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {}
```

```java
// SysUserRoleMapper.java
package com.medical.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.user.domain.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {}
```

```java
// WxUserBindingMapper.java
package com.medical.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.user.domain.entity.WxUserBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WxUserBindingMapper extends BaseMapper<WxUserBinding> {}
```

---

## Task 4: DTO/VO 定义

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/dto/LoginDTO.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/dto/RegisterDTO.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/dto/WxLoginDTO.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/dto/UserUpdateDTO.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/vo/UserVO.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/domain/vo/LoginVO.java`

**Step 1: LoginDTO**

```java
package com.medical.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
}
```

**Step 2: RegisterDTO**

```java
package com.medical.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度3-30位")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度6-30位")
    private String password;
    private String nickname;
    private String phone;
}
```

**Step 3: WxLoginDTO**

```java
package com.medical.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginDTO {
    @NotBlank(message = "code不能为空")
    private String code;
    /** 微信昵称 */
    private String nickname;
    /** 微信头像 */
    private String avatarUrl;
}
```

**Step 4: UserUpdateDTO**

```java
package com.medical.user.domain.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
}
```

**Step 5: UserVO**

```java
package com.medical.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private Integer gender;
    private Integer status;
    private List<String> roles;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
```

**Step 6: LoginVO**

```java
package com.medical.user.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class LoginVO {
    private String token;
    private UserVO user;
}
```

---

## Task 5: Sa-Token 权限适配

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/config/StpInterfaceImpl.java`

**Step 1: 实现 StpInterface**

```java
package com.medical.user.config;

import cn.dev33.satoken.stp.StpInterface;
import com.medical.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 本系统使用角色控制，不使用细粒度权限
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        return sysUserMapper.selectRoleKeysByUserId(userId);
    }
}
```

---

## Task 6: Service 层 - 认证服务

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/AuthService.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/impl/AuthServiceImpl.java`

**Step 1: AuthService 接口**

```java
package com.medical.user.service;

import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.vo.LoginVO;

public interface AuthService {
    /** 账号密码登录 */
    LoginVO login(LoginDTO dto);
    /** 用户注册 */
    void register(RegisterDTO dto);
    /** 微信小程序登录 */
    LoginVO wxLogin(WxLoginDTO dto);
    /** 退出登录 */
    void logout();
}
```

**Step 2: AuthServiceImpl**

```java
package com.medical.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.entity.SysRole;
import com.medical.user.domain.entity.SysUser;
import com.medical.user.domain.entity.SysUserRole;
import com.medical.user.domain.entity.WxUserBinding;
import com.medical.user.domain.vo.LoginVO;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.mapper.SysRoleMapper;
import com.medical.user.mapper.SysUserMapper;
import com.medical.user.mapper.SysUserRoleMapper;
import com.medical.user.mapper.WxUserBindingMapper;
import com.medical.user.service.AuthService;
import com.medical.user.service.WxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final WxUserBindingMapper wxUserBindingMapper;
    private final WxService wxService;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == UserConstants.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_PASSWORD_ERROR);
        }
        // 登录
        StpUtil.login(user.getId());
        return buildLoginVO(user);
    }

    @Override
    @Transactional
    public void register(RegisterDTO dto) {
        // 检查用户名是否存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setStatus(UserConstants.STATUS_NORMAL);
        userMapper.insert(user);

        // 默认分配患者角色
        SysRole patientRole = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, UserConstants.ROLE_PATIENT));
        if (patientRole != null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(patientRole.getId());
            userRoleMapper.insert(userRole);
        }
    }

    @Override
    @Transactional
    public LoginVO wxLogin(WxLoginDTO dto) {
        // 1. 调用微信 code2session
        WxService.WxSessionResult session = wxService.code2Session(dto.getCode());

        // 2. 查找是否已绑定
        WxUserBinding binding = wxUserBindingMapper.selectOne(
                new LambdaQueryWrapper<WxUserBinding>().eq(WxUserBinding::getOpenid, session.getOpenid()));

        SysUser user;
        if (binding != null) {
            // 已绑定，直接获取用户
            user = userMapper.selectById(binding.getUserId());
            // 更新 session_key
            binding.setSessionKey(session.getSessionKey());
            wxUserBindingMapper.updateById(binding);
        } else {
            // 未绑定，自动创建用户并绑定
            user = new SysUser();
            user.setUsername("wx_" + session.getOpenid().substring(0, 8));
            user.setNickname(dto.getNickname() != null ? dto.getNickname() : "微信用户");
            user.setAvatar(dto.getAvatarUrl());
            user.setStatus(UserConstants.STATUS_NORMAL);
            userMapper.insert(user);

            // 分配患者角色
            SysRole patientRole = roleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, UserConstants.ROLE_PATIENT));
            if (patientRole != null) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(patientRole.getId());
                userRoleMapper.insert(userRole);
            }

            // 创建绑定
            binding = new WxUserBinding();
            binding.setUserId(user.getId());
            binding.setOpenid(session.getOpenid());
            binding.setUnionid(session.getUnionid());
            binding.setSessionKey(session.getSessionKey());
            wxUserBindingMapper.insert(binding);
        }

        StpUtil.login(user.getId());
        return buildLoginVO(user);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    private LoginVO buildLoginVO(SysUser user) {
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());

        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUsername(user.getUsername());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
        userVO.setPhone(user.getPhone());
        userVO.setEmail(user.getEmail());
        userVO.setGender(user.getGender());
        userVO.setStatus(user.getStatus());
        userVO.setRoles(userMapper.selectRoleKeysByUserId(user.getId()));
        userVO.setCreateTime(user.getCreateTime());
        vo.setUser(userVO);
        return vo;
    }
}
```

---

## Task 7: WxService - 微信 code2session

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/WxService.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/impl/WxServiceImpl.java`

**Step 1: WxService 接口**

```java
package com.medical.user.service;

import lombok.Data;

public interface WxService {

    WxSessionResult code2Session(String code);

    @Data
    class WxSessionResult {
        private String openid;
        private String sessionKey;
        private String unionid;
    }
}
```

**Step 2: WxServiceImpl**

```java
package com.medical.user.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.user.service.WxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WxServiceImpl implements WxService {

    @Value("${wx.miniapp.appid:}")
    private String appid;

    @Value("${wx.miniapp.secret:}")
    private String secret;

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    @Override
    public WxSessionResult code2Session(String code) {
        String url = String.format(CODE2SESSION_URL, appid, secret, code);
        String response = HttpUtil.get(url, 5000);
        log.info("微信code2session响应: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            log.error("微信登录失败: errcode={}, errmsg={}", json.getInt("errcode"), json.getStr("errmsg"));
            throw new BusinessException(ErrorCode.WX_LOGIN_FAIL);
        }

        WxSessionResult result = new WxSessionResult();
        result.setOpenid(json.getStr("openid"));
        result.setSessionKey(json.getStr("session_key"));
        result.setUnionid(json.getStr("unionid"));
        return result;
    }
}
```

**Step 3: 在 application.yml 添加微信配置**

```yaml
# 追加到 medical-user-service 的 application.yml
wx:
  miniapp:
    appid: ${WX_APPID:your-appid}
    secret: ${WX_SECRET:your-secret}
```

---

## Task 8: Service 层 - 用户管理服务

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/SysUserService.java`
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/service/impl/SysUserServiceImpl.java`

**Step 1: SysUserService 接口**

```java
package com.medical.user.service;

import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.vo.UserVO;

public interface SysUserService {
    /** 分页查询用户列表 */
    PageResult<UserVO> listUsers(PageQuery pageQuery, String keyword);
    /** 根据ID查询用户 */
    UserVO getUserById(Long userId);
    /** 更新用户信息 */
    void updateUser(Long userId, UserUpdateDTO dto);
    /** 禁用/启用用户 */
    void toggleUserStatus(Long userId);
    /** 分配角色 */
    void assignRole(Long userId, String roleKey);
    /** 移除角色 */
    void removeRole(Long userId, String roleKey);
}
```

**Step 2: SysUserServiceImpl**

```java
package com.medical.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.entity.SysRole;
import com.medical.user.domain.entity.SysUser;
import com.medical.user.domain.entity.SysUserRole;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.mapper.SysRoleMapper;
import com.medical.user.mapper.SysUserMapper;
import com.medical.user.mapper.SysUserRoleMapper;
import com.medical.user.service.SysUserService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Override
    public PageResult<UserVO> listUsers(PageQuery pageQuery, String keyword) {
        Page<SysUser> page = new Page<>(pageQuery.getPageNum(), pageQuery.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);

        List<UserVO> records = result.getRecords().stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public UserVO getUserById(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toUserVO(user);
    }

    @Override
    public void updateUser(Long userId, UserUpdateDTO dto) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getGender() != null) user.setGender(dto.getGender());
        userMapper.updateById(user);
    }

    @Override
    public void toggleUserStatus(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(user.getStatus() == UserConstants.STATUS_NORMAL
                ? UserConstants.STATUS_DISABLED : UserConstants.STATUS_NORMAL);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRole(Long userId, String roleKey) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
        if (role == null) throw new BusinessException("角色不存在: " + roleKey);

        Long exists = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, role.getId()));
        if (exists > 0) return;

        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    @Override
    @Transactional
    public void removeRole(Long userId, String roleKey) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey));
        if (role == null) return;
        userRoleMapper.delete(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)
                        .eq(SysUserRole::getRoleId, role.getId()));
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setGender(user.getGender());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoles(userMapper.selectRoleKeysByUserId(user.getId()));
        return vo;
    }
}
```

---

## Task 9: Controller 层 - 认证接口

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/controller/AuthController.java`

**Step 1: AuthController**

```java
package com.medical.user.controller;

import com.medical.common.core.domain.R;
import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.vo.LoginVO;
import com.medical.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    @PostMapping("/wx-login")
    public R<LoginVO> wxLogin(@Valid @RequestBody WxLoginDTO dto) {
        return R.ok(authService.wxLogin(dto));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
```

---

## Task 10: Controller 层 - 用户管理接口

**Files:**
- Create: `medical-ai/medical-service/medical-user-service/src/main/java/com/medical/user/controller/SysUserController.java`

**Step 1: SysUserController**

```java
package com.medical.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.medical.common.core.constant.UserConstants;
import com.medical.common.core.domain.PageQuery;
import com.medical.common.core.domain.PageResult;
import com.medical.common.core.domain.R;
import com.medical.common.security.util.SecurityUtil;
import com.medical.user.domain.dto.UserUpdateDTO;
import com.medical.user.domain.vo.UserVO;
import com.medical.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
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
}
```

---

## Task 11: Feign API 定义 (供其他服务调用)

**Files:**
- Create: `medical-ai/medical-api/medical-user-api/src/main/java/com/medical/api/user/RemoteUserService.java`
- Create: `medical-ai/medical-api/medical-user-api/src/main/java/com/medical/api/user/dto/UserInfoDTO.java`

**Step 1: RemoteUserService Feign 接口**

```java
package com.medical.api.user;

import com.medical.api.user.dto.UserInfoDTO;
import com.medical.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "medical-user-service", path = "/user")
public interface RemoteUserService {

    @GetMapping("/inner/{userId}")
    R<UserInfoDTO> getUserById(@PathVariable("userId") Long userId);
}
```

**Step 2: UserInfoDTO**

```java
package com.medical.api.user.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserInfoDTO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private List<String> roles;
}
```

**Step 3: 在 user-service 中添加内部接口**

在 SysUserController 中添加：

```java
/** 内部调用 - 根据ID获取用户信息 */
@GetMapping("/inner/{userId}")
public R<UserInfoDTO> getInnerUser(@PathVariable Long userId) {
    UserVO vo = sysUserService.getUserById(userId);
    // 转换为 UserInfoDTO（简化版）
    UserInfoDTO dto = new UserInfoDTO();
    dto.setId(vo.getId());
    dto.setUsername(vo.getUsername());
    dto.setNickname(vo.getNickname());
    dto.setAvatar(vo.getAvatar());
    dto.setPhone(vo.getPhone());
    dto.setRoles(vo.getRoles());
    return R.ok(dto);
}
```

---

## Task 12: 编译验证 + Commit

**Step 1: 编译**

Run: `mvn clean compile -f medical-ai/pom.xml`
Expected: BUILD SUCCESS

**Step 2: Commit**

```bash
git add .
git commit -m "feat(user-service): implement auth (login/register/wx-login), user CRUD, role management"
```

---

## 检查清单

- [ ] DDL 包含 sys_user, sys_role, sys_user_role, wx_user_binding
- [ ] 初始化了 3 个角色 + 1 个 admin 账号
- [ ] 账号密码登录 + BCrypt 加密
- [ ] 微信小程序 code2session 登录 + 自动注册
- [ ] Sa-Token StpInterface 实现角色查询
- [ ] 用户 CRUD + 分页
- [ ] 禁用/启用用户
- [ ] 角色分配/移除
- [ ] Feign API 暴露用户查询接口
- [ ] 编译通过
