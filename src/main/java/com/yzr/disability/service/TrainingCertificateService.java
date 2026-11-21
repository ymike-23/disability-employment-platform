package com.yzr.disability.service;

import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.entity.dto.TrainingCertificateQueryDTO;
import com.yzr.disability.entity.vo.TrainingCertificateVO;
import java.util.List;

public interface TrainingCertificateService {
    PageResult<TrainingCertificateVO> page(TrainingCertificateQueryDTO query);
    TrainingCertificateVO detail(Long id);
    List<TrainingCertificateVO> byPerson(Long personId);
}

