package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.vo.SysUserVO;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统用户业务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    /** 新建账号固定使用业务人员角色 */
    private static final String BUSINESS_ROLE_CODE = "BUSINESS";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public SysUserVO createBusinessUser(CreateUserDTO dto) {
        String username = dto.getUsername().trim();

        // 1. 用户名查重（数据库 uk_username 唯一索引兜底并发场景）
        LambdaQueryWrapper<SysUserPO> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserPO::getUsername, username);
        if (sysUserMapper.selectCount(userWrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 2. 查询启用的业务人员角色（按 role_code 查询，不硬编码 ID）
        LambdaQueryWrapper<SysRolePO> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRolePO::getRoleCode, BUSINESS_ROLE_CODE)
                .eq(SysRolePO::getStatus, 1);
        SysRolePO role = sysRoleMapper.selectOne(roleWrapper);
        if (role == null) {
            throw new BusinessException("业务人员角色未配置或已停用，请联系管理员处理");
        }

        // 3. 创建账号（BCrypt 加密，不存明文）
        SysUserPO user = new SysUserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setDeleted(0);
        sysUserMapper.insert(user);

        // 4. 绑定业务人员角色（与建号在同一事务，避免出现无角色账号）
        SysUserRolePO userRole = new SysUserRolePO();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        sysUserRoleMapper.insert(userRole);

        // 5. 组装返回（不含密码）
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setStatus(user.getStatus());
        vo.setRoleCode(BUSINESS_ROLE_CODE);
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}