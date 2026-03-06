package com.medical.user.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度3-30位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度6-30位")
    private String password;

    private String nickname;
    private String phone;
    private String email;
    private Integer gender; // 0-未知 1-男 2-女
    private Integer status; // 0-正常 1-禁用

    /** 初始角色Key，如 "admin", "doctor", "patient" */
    private String roleKey;
}
