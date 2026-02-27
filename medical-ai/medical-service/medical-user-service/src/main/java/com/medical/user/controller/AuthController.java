package com.medical.user.controller;

import com.medical.common.core.domain.R;
import com.medical.user.domain.dto.LoginDTO;
import com.medical.user.domain.dto.RegisterDTO;
import com.medical.user.domain.dto.WxLoginDTO;
import com.medical.user.domain.vo.LoginVO;
import com.medical.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    @PostMapping("/wx-login")
    public R<LoginVO> wxLogin(@Valid @RequestBody WxLoginDTO dto) {
        return R.ok(authService.wxLogin(dto));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
