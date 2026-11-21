package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.po.SysDictPO;
import com.yzr.disability.entity.vo.DictOptionVO;
import com.yzr.disability.mapper.SysDictMapper;
import com.yzr.disability.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DictServiceImpl implements DictService {

    @Autowired
    private SysDictMapper sysDictMapper;

    @Override
    public List<DictOptionVO> list(String dictType) {
        if (!SecurityUtils.hasRole("ADMIN") && !SecurityUtils.hasRole("BUSINESS")) {
            throw new BusinessException(403, "当前账号无字典访问权限");
        }
        if (!StringUtils.hasText(dictType) || dictType.length() > 50) {
            throw new BusinessException(400, "字典类型不能为空且长度不能超过50");
        }
        LambdaQueryWrapper<SysDictPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictPO::getDictType, dictType.trim())
                .eq(SysDictPO::getStatus, 1)
                .orderByAsc(SysDictPO::getSortOrder)
                .orderByAsc(SysDictPO::getId)
                .select(SysDictPO::getDictValue, SysDictPO::getDictLabel);
        return sysDictMapper.selectList(wrapper).stream()
                .map(dict -> new DictOptionVO(dict.getDictValue(), dict.getDictLabel()))
                .toList();
    }
}
