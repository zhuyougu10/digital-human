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
