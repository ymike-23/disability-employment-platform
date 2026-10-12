package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.DisabledPersonQueryDTO;
import com.yzr.disability.entity.dto.DisabledPersonSaveDTO;
import com.yzr.disability.entity.vo.DisabledPersonVO;

/**
 * 残疾人档案业务服务。
 *
 * <p>所有接口均实施网格数据范围控制：
 * 管理员可访问全部；业务人员仅限其唯一有效网格。
 */
public interface DisabledPersonService {

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
