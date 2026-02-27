package com.medical.user.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.medical.common.core.exception.BusinessException;
import com.medical.common.core.exception.ErrorCode;
import com.medical.user.service.WxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WxServiceImpl implements WxService {

    @Value("${wx.miniapp.appid:}")
    private String appid;

    @Value("${wx.miniapp.secret:}")
    private String secret;

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    @Override
    public WxSessionResult code2Session(String code) {
        String url = String.format(CODE2SESSION_URL, appid, secret, code);
        String response = HttpUtil.get(url, 5000);
        log.info("微信code2session响应: {}", response);

        JSONObject json = JSONUtil.parseObj(response);
        if (json.containsKey("errcode") && json.getInt("errcode") != 0) {
            log.error("微信登录失败: errcode={}, errmsg={}", json.getInt("errcode"), json.getStr("errmsg"));
            throw new BusinessException(ErrorCode.WX_LOGIN_FAIL);
        }

        WxSessionResult result = new WxSessionResult();
        result.setOpenid(json.getStr("openid"));
        result.setSessionKey(json.getStr("session_key"));
        result.setUnionid(json.getStr("unionid"));
        return result;
    }
}
