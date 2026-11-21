package com.yzr.disability.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员创建业务人员账号入参。
 *
 * <p>注意：本 DTO 不提供角色字段，角色由服务端固定为 BUSINESS，
 * 因此无法用于创建 ADMIN 账号。
 */
@Data
public class CreateUserDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    /** 初始密码：最低 6 位，由管理员指定，服务端 BCrypt 加密后存储 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度需在 6 到 50 之间")
    private String password;

    @Schema(description = "启用且未分配的真实网格 ID，通过 GET /api/admin/grids 获取", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择网格")
    @Positive(message = "网格 ID 必须大于 0")
    private Long gridId;
}
