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
