package com.yzr.disability.service;

import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.vo.SysUserVO;

/**
 * 系统用户业务服务。
 */
public interface UserService {

    /**
     * 创建业务人员账号（仅管理员可调用）。
     *
     * <p>在事务内完成：创建 sys_user + 绑定 BUSINESS 角色。
     * 角色由服务端固定为 BUSINESS，不支持创建 ADMIN 账号。
     *
     * @return 新建账号信息（不含密码）
     */
    SysUserVO createBusinessUser(CreateUserDTO dto);
}