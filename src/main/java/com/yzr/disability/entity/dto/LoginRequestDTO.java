package com.yzr.disability.entity.dto;

import lombok.Data;

/**
 * 登录请求参数 DTO。
 */
@Data
public class LoginRequestDTO {

    private String username;

    private String password;
}