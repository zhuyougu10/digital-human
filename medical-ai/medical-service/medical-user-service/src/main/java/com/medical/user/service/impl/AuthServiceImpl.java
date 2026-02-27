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
