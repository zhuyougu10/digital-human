package com.medical.common.security.config;

import cn.dev33.satoken.stp.StpInterface;
import com.medical.api.user.RemoteUserService;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(StpInterface.class)
public class StpInterfaceImpl implements StpInterface {

    private final RemoteUserService remoteUserService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // This project currently checks authorization by role only.
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        R<UserInfoDTO> result = remoteUserService.getUserById(userId);
        if (result == null || !result.isSuccess() || result.getData() == null || result.getData().getRoles() == null) {
            return Collections.emptyList();
        }
        return result.getData().getRoles();
    }
}
