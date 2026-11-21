package com.yzr.disability.service;

import com.yzr.disability.entity.vo.DictOptionVO;

import java.util.List;

public interface DictService {

    List<DictOptionVO> list(String dictType);
}
