package com.medical.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/live2d")
public class Live2dAuthController {

    @GetMapping("/auth-check")
    public ResponseEntity<Void> authCheck() {
        return ResponseEntity.noContent().build();
    }
}
