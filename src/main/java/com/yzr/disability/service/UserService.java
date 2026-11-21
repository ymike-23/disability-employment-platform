package com.yzr.disability.service;

import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.dto.BusinessUserQueryDTO;
import com.yzr.disability.entity.dto.UpdateBusinessUserDTO;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.vo.GridOptionVO;
import com.yzr.disability.entity.vo.SysUserVO;

import java.util.List;

/**
 * 系统用户业务服务。
 */
public interface UserService {

    /**
     * 创建业务人员账号（仅管理员可调用）。
     *
     * <p>在事务内完成：创建 sys_user + 绑定 BUSINESS 角色 + 分配未占用网格。
     * 角色由服务端固定为 BUSINESS，不支持创建 ADMIN 账号。
     *
     * @return 新建账号信息（不含密码）
     */
    SysUserVO createBusinessUser(CreateUserDTO dto);

    /** 查询启用、未删除且未分配的网格，仅管理员可调用。 */
    List<GridOptionVO> listAssignableGrids();

    PageResult<SysUserVO> pageBusinessUsers(BusinessUserQueryDTO query);

    SysUserVO getBusinessUser(Long id);

    SysUserVO updateBusinessUser(Long id, UpdateBusinessUserDTO dto);

    /** 逻辑删除账号并释放网格，保留角色及业务历史。 */
    void deleteBusinessUser(Long id);
}
