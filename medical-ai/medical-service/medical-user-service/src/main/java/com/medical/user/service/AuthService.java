package com.medical.user.service;

import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.vo.LoginVO;

public interface AuthService {
    /** 账号密码登录 */
    LoginVO login(LoginDTO dto);
    /** 用户注册 */
    void register(RegisterDTO dto);
    /** 微信小程序登录 */
    LoginVO wxLogin(WxLoginDTO dto);
    /** 退出登录 */
    void logout();
}
