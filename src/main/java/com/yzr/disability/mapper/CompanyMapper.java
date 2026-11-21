package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.CompanyPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CompanyMapper extends BaseMapper<CompanyPO> {

    @Select("SELECT * FROM company WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CompanyPO selectForUpdate(@Param("id") Long id);

    // 与岗位写操作使用相同的企业行锁，锁定读避免可重复读快照遗漏并发新增岗位。
    @Select("SELECT id FROM job WHERE company_id = #{companyId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    Long selectActiveJobForUpdate(@Param("companyId") Long companyId);
}
