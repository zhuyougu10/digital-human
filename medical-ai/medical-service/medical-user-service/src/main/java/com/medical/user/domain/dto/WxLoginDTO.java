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
