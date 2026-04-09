package com.medical.appointment.config;

import cn.dev33.satoken.stp.StpInterface;
import com.medical.api.user.RemoteUserService;
import com.medical.api.user.dto.UserInfoDTO;
import com.medical.common.core.domain.R;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component("appointmentStpInterface")
@Primary
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final RemoteUserService remoteUserService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
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
