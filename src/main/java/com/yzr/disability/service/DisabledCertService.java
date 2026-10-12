package com.yzr.disability.service;

import com.yzr.disability.entity.dto.DisabledCertQueryDTO;
import com.yzr.disability.entity.dto.DisabledCertSaveDTO;
import com.yzr.disability.entity.vo.DisabledCertVO;

import java.util.List;
import java.util.Set;

/** 独立证件服务，沿用人员档案的 ADMIN/BUSINESS 网格权限。 */
public interface DisabledCertService {

    String CERT_STATUS_NORMAL = "正常";
    String CERT_STATUS_RENEWING = "换证中";
    String CERT_STATUS_CANCELLED = "注销";
    String CERT_STATUS_LOST = "挂失";
    Set<String> VALID_CERT_STATUSES = Set.of(
            CERT_STATUS_NORMAL, CERT_STATUS_RENEWING, CERT_STATUS_CANCELLED, CERT_STATUS_LOST);
    Set<String> EXPIRY_EXCLUDED_CERT_STATUSES = Set.of(CERT_STATUS_CANCELLED, CERT_STATUS_LOST);
    int DEFAULT_EXPIRE_WITHIN_DAYS = 30;

    List<DisabledCertVO> list(DisabledCertQueryDTO query);

    /** 没有当前证件时返回 null；多条当前记录视为数据异常。 */
    DisabledCertVO current(Long personId);

    Long create(DisabledCertSaveDTO dto);

    void update(Long id, DisabledCertSaveDTO dto);

    void delete(Long id);
}
