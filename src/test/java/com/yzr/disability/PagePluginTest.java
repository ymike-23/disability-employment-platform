package com.yzr.disability;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页插件集成验证（仅验证基础设施，不属于业务模块）。
 */
@SpringBootTest
class PagePluginTest {

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Test
    @DisplayName("✅ MybatisPlusInterceptor 已注册 PaginationInnerInterceptor")
    void paginationInterceptorRegistered() {
        assertNotNull(mybatisPlusInterceptor, "MybatisPlusInterceptor 应被 Spring 注册");

        boolean contains = mybatisPlusInterceptor.getInterceptors().stream()
                .anyMatch(i -> i instanceof PaginationInnerInterceptor);

        assertTrue(contains, "拦截器链中应包含 PaginationInnerInterceptor");
        System.out.println("✅ 分页拦截器已注册，拦截器数量: "
                + mybatisPlusInterceptor.getInterceptors().size());
    }

    @Test
    @DisplayName("✅ selectPage 分页查询生效（只读）")
    void selectPageWorks() {
        Page<SysUserPO> page = new Page<>(1, 2);
        sysUserMapper.selectPage(page, null);

        assertNotNull(page.getRecords(), "records 不应为 null");
        assertTrue(page.getRecords().size() <= 2, "每页条数应 <= 2");
        assertEquals(1, page.getCurrent(), "当前页应为 1");
        assertEquals(2, page.getSize(), "每页大小应为 2");
        assertTrue(page.getTotal() >= 0, "total 应 >= 0");

        System.out.println("✅ 分页查询成功: total=" + page.getTotal()
                + ", pages=" + page.getPages()
                + ", records=" + page.getRecords().size());
    }
}