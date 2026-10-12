package com.yzr.disability.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录返回结果 VO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseVO {

    private String token;

    private String username;

    private String realName;
}