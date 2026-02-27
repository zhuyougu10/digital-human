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
