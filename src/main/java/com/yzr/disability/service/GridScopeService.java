package com.yzr.disability.service;

import java.util.List;

/**
 * 网格数据范围服务。
 *
 * <p>仅使用 ADMIN、BUSINESS 两个角色：
 * <ul>
 *   <li>ADMIN：可访问全部数据，不附加网格条件</li>
 *   <li>BUSINESS：只能访问其唯一有效网格内的数据</li>
 * </ul>
 */
public interface GridScopeService {

    /** 多网格配置异常提示（不返回网格 ID 等内部信息） */
    String MULTI_GRID_MESSAGE = "当前账号关联多个网格，无法执行操作，请联系管理员处理";

    /** 无有效网格提示 */
    String NO_GRID_MESSAGE = "当前账号未关联有效网格，请联系管理员处理";

    /**
     * 当前登录用户是否为管理员
     */
    boolean isAdmin();

    /**
     * 当前业务人员关联的网格 ID 列表（仅有效网格）。
     *
     * <p>管理员返回空列表表示"全部"，调用方需先用 {@link #isAdmin()} 判断。
     * 业务人员可能返回 0、1 或多个（多网格属配置异常）。
     */
    List<Long> getCurrentGridIds();

    /**
     * 当前业务人员唯一有效网格 ID。
     *
     * @throws com.yzr.disability.common.exception.BusinessException
     *         未关联网格，或关联多个网格（配置异常，拒绝访问而非扩大权限）
     */
    Long requireSingleGridId();
}