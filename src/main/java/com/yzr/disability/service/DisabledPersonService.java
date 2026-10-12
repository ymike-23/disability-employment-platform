package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.DisabledPersonQueryDTO;
import com.yzr.disability.entity.dto.DisabledPersonSaveDTO;
import com.yzr.disability.entity.vo.DisabledPersonVO;

import java.util.Set;

/**
 * 残疾人档案业务服务。
 *
 * <p>所有接口均实施网格数据范围控制：
 * 管理员可访问全部；业务人员仅限其唯一有效网格。
 */
public interface DisabledPersonService {

    // ==================== 证件状态常量 ====================
    // 沿用项目现有编码风格：枚举值以中文字符串存库（与 disability_type、disability_level 一致）

    /** 证件状态：正常 */
    String CERT_STATUS_NORMAL = "正常";

    /** 证件状态：换证中 */
    String CERT_STATUS_RENEWING = "换证中";

    /** 证件状态：注销 */
    String CERT_STATUS_CANCELLED = "注销";

    /** 证件状态：挂失 */
    String CERT_STATUS_LOST = "挂失";

    /** 合法证件状态集合（顺序无关，用于校验） */
    Set<String> VALID_CERT_STATUSES =
            Set.of(CERT_STATUS_NORMAL, CERT_STATUS_RENEWING, CERT_STATUS_CANCELLED, CERT_STATUS_LOST);

    /** 到期预警排除的状态：已注销、已挂失的证件不再预警 */
    Set<String> EXPIRY_EXCLUDED_CERT_STATUSES = Set.of(CERT_STATUS_CANCELLED, CERT_STATUS_LOST);

    /** 到期预警默认天数 */
    int DEFAULT_EXPIRE_WITHIN_DAYS = 30;

    /**
     * 分页查询
     */
    PageResult<DisabledPersonVO> page(DisabledPersonQueryDTO query);

    /**
     * 详情（校验数据归属）
     */
    DisabledPersonVO detail(Long id);

    /**
     * 新增（业务人员的 gridId 由后端解析，不信任前端传值）
     *
     * @return 新档案 ID
     */
    Long create(DisabledPersonSaveDTO dto);

    /**
     * 修改（WHERE 条件中落实数据权限）
     */
    void update(Long id, DisabledPersonSaveDTO dto);

    /**
     * 逻辑删除（WHERE 条件中落实数据权限）
     */
    void delete(Long id);
}