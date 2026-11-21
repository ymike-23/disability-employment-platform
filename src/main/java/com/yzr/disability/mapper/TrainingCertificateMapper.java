package com.yzr.disability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzr.disability.entity.po.TrainingCertificatePO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TrainingCertificateMapper extends BaseMapper<TrainingCertificatePO> {
    @Select("SELECT * FROM training_certificate WHERE training_record_id = #{recordId} FOR UPDATE")
    TrainingCertificatePO selectByRecordForUpdate(@Param("recordId") Long recordId);
}

