package com.yzr.disability;

import com.yzr.disability.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用受保护端点。
 *
 * <p>仅存在于测试环境（src/test），由测试类通过 {@code @Import} 引入，
 * 用于验证 JWT / Redis / Security 认证链路，不会进入正式业务代码。
 */
@RestController
@RequestMapping("/api/test")
public class TestProtectedController {

    /**
     * 受保护测试端点：通过 Security 认证后返回固定结果。
     */
    @GetMapping("/protected")
    public Result<String> protectedEndpoint() {
        return Result.success("认证通过");
    }
}