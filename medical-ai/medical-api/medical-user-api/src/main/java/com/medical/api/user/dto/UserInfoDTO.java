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
