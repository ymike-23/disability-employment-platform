package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.security.LoginUser;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * 系统实际使用的业务角色白名单。
     * GRID_WORKER 在数据库中保留记录，但不参与系统鉴权。
     */
    private static final Set<String> ENABLED_ROLE_CODES = Set.of("ADMIN", "BUSINESS");

    private static final String ROLE_PREFIX = "ROLE_";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<SysUserPO> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserPO::getUsername, username);
        SysUserPO sysUser = sysUserMapper.selectOne(userWrapper);

        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        boolean enabled = !(sysUser.getStatus() == null || sysUser.getStatus() == 0);

        return new LoginUser(
                sysUser.getId(),
                sysUser.getUsername(),
                sysUser.getPassword(),
                enabled,
                loadAuthorities(sysUser.getId())
        );
    }

    /**
     * 加载用户角色权限：sys_user_role -> sys_role -> ROLE_{roleCode}
     */
    private Set<GrantedAuthority> loadAuthorities(Long userId) {
        // 使用 LinkedHashSet 去重，避免重复关联产生重复 Authority
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (userId == null) {
            return authorities;
        }

        // 1. 查询用户角色关联
        LambdaQueryWrapper<SysUserRolePO> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(SysUserRolePO::getUserId, userId);
        List<SysUserRolePO> userRoles = sysUserRoleMapper.selectList(userRoleWrapper);

        if (userRoles.isEmpty()) {
            return authorities;
        }

        List<Long> roleIds = userRoles.stream()
                .map(SysUserRolePO::getRoleId)
                .filter(id -> id != null)
                .toList();

        if (roleIds.isEmpty()) {
            return authorities;
        }

        // 2. 查询启用状态的有效角色（deleted 由 @TableLogic 自动过滤）
        LambdaQueryWrapper<SysRolePO> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(SysRolePO::getId, roleIds)
                .eq(SysRolePO::getStatus, 1);
        List<SysRolePO> roles = sysRoleMapper.selectList(roleWrapper);

        // 3. role_code -> ROLE_xxx（仅白名单角色）
        for (SysRolePO role : roles) {
            String roleCode = role.getRoleCode();
            if (roleCode == null || roleCode.isBlank()) {
                continue;
            }
            if (!ENABLED_ROLE_CODES.contains(roleCode)) {
                continue;
            }
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleCode));
        }

        return authorities;
    }
}