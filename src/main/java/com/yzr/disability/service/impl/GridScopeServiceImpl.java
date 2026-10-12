package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.service.GridScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GridScopeServiceImpl implements GridScopeService {

    @Autowired
    private SysGridMapper sysGridMapper;

    @Override
    public boolean isAdmin() {
        // 入口校验：仅 ADMIN、BUSINESS 可进入本服务
        requireArchiveRole();
        return SecurityUtils.hasRole("ADMIN");
    }

    @Override
    public List<Long> getCurrentGridIds() {
        Long userId = requireUserId();
        // 入口校验：仅 ADMIN、BUSINESS 可进入本服务
        requireArchiveRole();
        if (SecurityUtils.hasRole("ADMIN")) {
            // 管理员不限制网格，返回空列表表示"全部"
            return List.of();
        }

        LambdaQueryWrapper<SysGridPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysGridPO::getGridWorkerId, userId)
                .eq(SysGridPO::getStatus, 1);
        // deleted 由 @TableLogic 自动过滤

        return sysGridMapper.selectList(wrapper).stream()
                .map(SysGridPO::getId)
                .toList();
    }

    @Override
    public Long requireSingleGridId() {
        List<Long> gridIds = getCurrentGridIds();
        if (isAdmin()) {
            throw new BusinessException("管理员请显式指定网格");
        }
        if (gridIds.isEmpty()) {
            throw new BusinessException(NO_GRID_MESSAGE);
        }
        if (gridIds.size() > 1) {
            // 配置异常：拒绝访问，绝不扩大权限范围
            throw new BusinessException(MULTI_GRID_MESSAGE);
        }
        return gridIds.get(0);
    }

    /**
     * 角色准入校验：仅允许 ADMIN、BUSINESS 访问档案数据。
     *
     * <p>未登录用户由 Spring Security 认证机制拦截；此处防止已登录但
     * 既无 ADMIN 也无 BUSINESS 角色（如仅 GRID_WORKER）的账号被当作
     * 业务人员放行，实现纵深防御。
     */
    private void requireArchiveRole() {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("BUSINESS")) {
            throw new BusinessException(403, "当前账号无档案访问权限");
        }
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        return userId;
    }
}