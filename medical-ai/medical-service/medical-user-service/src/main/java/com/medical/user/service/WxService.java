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
