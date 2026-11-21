package com.yzr.disability.service;

import com.yzr.disability.entity.dto.DisabledCertQueryDTO;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
import com.yzr.disability.entity.vo.DisabledCertVO;

import java.util.List;

/** 独立证件服务，沿用人员档案的 ADMIN/BUSINESS 网格权限。 */
public interface DisabledCertService {

    /** cert_status.dict_value；新建时保留原有的正常状态语义。 */
    String CERT_STATUS_NORMAL = "1";
    String CERT_STATUS_RENEWING = "2";
    String CERT_STATUS_CANCELLED = "3";

    List<DisabledCertVO> list(DisabledCertQueryDTO query);

    /** 没有当前证件时返回 null；多条当前记录视为数据异常。 */
    DisabledCertVO current(Long personId);

    Long create(DisabledCertSaveDTO dto);

    void update(Long id, DisabledCertSaveDTO dto);

    void delete(Long id);
}
