package com.yzr.disability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yzr.disability.common.exception.BusinessException;
import com.yzr.disability.common.result.PageResult;
import com.yzr.disability.common.security.SecurityUtils;
import com.yzr.disability.entity.dto.CreateUserDTO;
import com.yzr.disability.entity.dto.BusinessUserQueryDTO;
import com.yzr.disability.entity.dto.UpdateBusinessUserDTO;
import com.yzr.disability.entity.po.SysGridPO;
import com.yzr.disability.entity.po.SysRolePO;
import com.yzr.disability.entity.po.SysUserPO;
import com.yzr.disability.entity.po.SysUserRolePO;
import com.yzr.disability.entity.vo.GridOptionVO;
import com.yzr.disability.entity.vo.SysUserVO;
import com.yzr.disability.mapper.SysGridMapper;
import com.yzr.disability.mapper.SysRoleMapper;
import com.yzr.disability.mapper.SysUserMapper;
import com.yzr.disability.mapper.SysUserRoleMapper;
import com.yzr.disability.service.UserService;
import com.yzr.disability.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统用户业务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    /** 新建账号固定使用业务人员角色 */
    private static final String BUSINESS_ROLE_CODE = "BUSINESS";

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysGridMapper sysGridMapper;

    @Autowired
    private TokenService tokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserVO createBusinessUser(CreateUserDTO dto) {
        requireAdmin();
        if (dto == null) throw new BusinessException(400, "账号信息不能为空");
        validateUsername(dto.getUsername());
        if (dto.getPassword() == null || dto.getPassword().isBlank() || dto.getPassword().length() < 6 || dto.getPassword().length() > 50)
            throw new BusinessException(400, "密码长度需在6到50之间且不能全为空白");
        if (dto.getGridId() == null || dto.getGridId() <= 0) {
            throw new BusinessException(400, "请选择有效的网格 ID");
        }
        String username = dto.getUsername().trim();

        // 1. 用户名查重（数据库 uk_username 唯一索引兜底并发场景）
        LambdaQueryWrapper<SysUserPO> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserPO::getUsername, username);
        if (sysUserMapper.selectCount(userWrapper) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 2. 查询启用的业务人员角色（按 role_code 查询，不硬编码 ID）
        LambdaQueryWrapper<SysRolePO> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(SysRolePO::getRoleCode, BUSINESS_ROLE_CODE)
                .eq(SysRolePO::getStatus, 1);
        SysRolePO role = sysRoleMapper.selectOne(roleWrapper);
        if (role == null) {
            throw new BusinessException(403, "业务人员角色未配置或已停用，请联系管理员处理");
        }

        SysGridPO grid = sysGridMapper.selectForUpdate(dto.getGridId());
        if (grid == null || !Integer.valueOf(1).equals(grid.getStatus())) {
            throw new BusinessException(400, "网格不存在、已删除或未启用");
        }
        if (grid.getGridWorkerId() != null) {
            throw new BusinessException(409, "网格已分配，请重新选择");
        }

        // 3. 创建账号（BCrypt 加密，不存明文）
        SysUserPO user = new SysUserPO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(user.getCreateTime());
        try {
            if (sysUserMapper.insert(user) != 1) {
                throw new BusinessException(409, "账号创建失败，请重试");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 4. 绑定业务人员角色（与建号在同一事务，避免出现无角色账号）
        SysUserRolePO userRole = new SysUserRolePO();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        if (sysUserRoleMapper.insert(userRole) != 1) {
            throw new BusinessException(409, "账号角色绑定失败，请重试");
        }

        // 条件更新不接管已有归属，失败时连同账号、角色一起回滚。
        int assigned = sysGridMapper.update(null, new LambdaUpdateWrapper<SysGridPO>()
                .eq(SysGridPO::getId, grid.getId())
                .eq(SysGridPO::getStatus, 1)
                .isNull(SysGridPO::getGridWorkerId)
                .set(SysGridPO::getGridWorkerId, user.getId())
                .set(SysGridPO::getGridWorkerName, username)
                .set(SysGridPO::getUpdateTime, LocalDateTime.now()));
        if (assigned != 1) {
            throw new BusinessException(409, "网格已不可分配，请重新选择");
        }

        // 5. 组装返回（不含密码）
        return toVO(user, List.of(grid));
    }

    @Override
    public List<GridOptionVO> listAssignableGrids() {
        requireAdmin();
        return sysGridMapper.selectList(new LambdaQueryWrapper<SysGridPO>()
                        .eq(SysGridPO::getStatus, 1)
                        .isNull(SysGridPO::getGridWorkerId)
                        .orderByAsc(SysGridPO::getId))
                .stream().map(this::gridOption).toList();
    }

    @Override
    public PageResult<SysUserVO> pageBusinessUsers(BusinessUserQueryDTO query) {
        requireAdmin();
        if (query == null) throw new BusinessException(400, "查询条件不能为空");
        if (query.getUsername() != null && query.getUsername().length() > 50)
            throw new BusinessException(400, "用户名长度不能超过50");
        if (query.getStatus() != null) validateStatus(query.getStatus());
        long current = query.getCurrent() == null ? 1 : query.getCurrent();
        long size = query.getSize() == null ? 10 : query.getSize();
        if (current < 1 || size < 1 || size > 100) throw new BusinessException(400, "分页参数无效，每页最多100条");
        var wrapper = businessUsers();
        if (query.getUsername() != null && !query.getUsername().isBlank()) wrapper.like(SysUserPO::getUsername, query.getUsername().trim());
        if (query.getStatus() != null) wrapper.eq(SysUserPO::getStatus, query.getStatus());
        wrapper.orderByDesc(SysUserPO::getId);
        Page<SysUserPO> page = sysUserMapper.selectPage(new Page<>(current, size), wrapper);
        Map<Long, List<SysGridPO>> grids = page.getRecords().isEmpty() ? Map.of() : sysGridMapper.selectList(new LambdaQueryWrapper<SysGridPO>()
                .in(SysGridPO::getGridWorkerId, page.getRecords().stream().map(SysUserPO::getId).toList())
                .orderByAsc(SysGridPO::getId)).stream().collect(Collectors.groupingBy(SysGridPO::getGridWorkerId));
        return new PageResult<>(page.getRecords().stream().map(user -> toVO(user, grids.getOrDefault(user.getId(), List.of()))).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    @Override
    public SysUserVO getBusinessUser(Long id) {
        requireAdmin();
        return toVO(requireBusinessUser(id, false), userGrids(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUserVO updateBusinessUser(Long id, UpdateBusinessUserDTO dto) {
        requireAdmin();
        SysUserPO user = requireBusinessUser(id, true);
        if (dto == null) throw new BusinessException(400, "账号信息不能为空");
        validateUsername(dto.getUsername());
        validateStatus(dto.getStatus());
        if (dto.getGridId() == null || dto.getGridId() <= 0) throw new BusinessException(400, "请选择有效网格");
        List<SysGridPO> previous = userGrids(id);
        if (previous.size() > 1) throw new BusinessException(409, "该账号绑定了多个网格，请先核对配置，不能自动覆盖");
        List<SysGridPO> locked = lockGrids(previous, dto.getGridId());
        SysGridPO target = locked.stream().filter(grid -> grid.getId().equals(dto.getGridId())).findFirst().orElseThrow();
        boolean retaining = previous.stream().anyMatch(grid -> grid.getId().equals(target.getId()));
        if (!retaining && !Integer.valueOf(1).equals(target.getStatus())) throw new BusinessException(400, "网格未启用");
        if (target.getGridWorkerId() != null && !id.equals(target.getGridWorkerId())) throw new BusinessException(409, "网格已分配，请重新选择");
        String username = dto.getUsername().trim();
        SysUserPO changes = new SysUserPO();
        changes.setId(id);
        changes.setUsername(username);
        changes.setStatus(dto.getStatus());
        changes.setUpdateTime(LocalDateTime.now());
        try {
            if (sysUserMapper.updateById(changes) != 1) throw new BusinessException(409, "账号已变更，请刷新重试");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "用户名已存在");
        }
        for (SysGridPO grid : previous) if (!grid.getId().equals(target.getId())) releaseGrid(grid.getId(), id);
        var assignment = new LambdaUpdateWrapper<SysGridPO>().eq(SysGridPO::getId, target.getId());
        if (retaining) assignment.eq(SysGridPO::getGridWorkerId, id);
        else assignment.isNull(SysGridPO::getGridWorkerId).eq(SysGridPO::getStatus, 1);
        assignment.set(SysGridPO::getGridWorkerId, id).set(SysGridPO::getGridWorkerName, username)
                .set(SysGridPO::getUpdateTime, changes.getUpdateTime());
        if (sysGridMapper.update(null, assignment) != 1) throw new BusinessException(409, "网格归属已变化，请刷新重试");
        tokenService.deleteToken(id);
        user.setUsername(username);
        user.setStatus(dto.getStatus());
        user.setUpdateTime(changes.getUpdateTime());
        return toVO(user, List.of(target));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBusinessUser(Long id) {
        requireAdmin();
        requireBusinessUser(id, true);
        List<SysGridPO> grids = userGrids(id);
        lockGrids(grids, null);
        for (SysGridPO grid : grids) releaseGrid(grid.getId(), id);
        if (sysUserMapper.update(null, new LambdaUpdateWrapper<SysUserPO>().eq(SysUserPO::getId, id)
                .set(SysUserPO::getStatus, 0).set(SysUserPO::getUpdateTime, LocalDateTime.now())) != 1
                || sysUserMapper.deleteById(id) != 1) throw new BusinessException(409, "账号已变更，请刷新重试");
        tokenService.deleteToken(id);
    }

    private LambdaQueryWrapper<SysUserPO> businessUsers() {
        return new LambdaQueryWrapper<SysUserPO>()
                .exists("SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = sys_user.id AND r.role_code = 'BUSINESS' AND r.deleted = 0")
                .notExists("SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = sys_user.id AND r.role_code = 'ADMIN' AND r.deleted = 0");
    }

    private SysUserPO requireBusinessUser(Long id, boolean lock) {
        if (id == null || id <= 0) throw new BusinessException(400, "账号ID必须大于0");
        SysUserPO user = lock ? sysUserMapper.selectForUpdate(id) : sysUserMapper.selectById(id);
        if (user == null || sysUserMapper.selectCount(businessUsers().eq(SysUserPO::getId, id)) == 0)
            throw new BusinessException(404, "业务人员不存在、已删除或不可维护");
        return user;
    }

    private List<SysGridPO> userGrids(Long id) {
        return sysGridMapper.selectList(new LambdaQueryWrapper<SysGridPO>().eq(SysGridPO::getGridWorkerId, id).orderByAsc(SysGridPO::getId));
    }

    private List<SysGridPO> lockGrids(List<SysGridPO> previous, Long targetId) {
        List<Long> ids = new ArrayList<>(previous.stream().map(SysGridPO::getId).toList());
        if (targetId != null) ids.add(targetId);
        List<SysGridPO> locked = new ArrayList<>();
        // 所有账号操作按网格ID排序加锁，避免更换网格时锁序相反。
        for (Long id : ids.stream().distinct().sorted().toList()) {
            SysGridPO grid = sysGridMapper.selectForUpdate(id);
            if (grid == null) throw new BusinessException(400, "网格不存在或已删除");
            if (previous.stream().anyMatch(old -> old.getId().equals(id) && !Objects.equals(old.getGridWorkerId(), grid.getGridWorkerId())))
                throw new BusinessException(409, "网格归属已变化，请刷新重试");
            locked.add(grid);
        }
        return locked;
    }

    private void releaseGrid(Long gridId, Long userId) {
        if (sysGridMapper.update(null, new LambdaUpdateWrapper<SysGridPO>().eq(SysGridPO::getId, gridId)
                .eq(SysGridPO::getGridWorkerId, userId).set(SysGridPO::getGridWorkerId, null)
                .set(SysGridPO::getGridWorkerName, null).set(SysGridPO::getUpdateTime, LocalDateTime.now())) != 1)
            throw new BusinessException(409, "网格归属已变化，请刷新重试");
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank() || username.length() > 50)
            throw new BusinessException(400, "用户名不能为空且不能超过50个字符");
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) throw new BusinessException(400, "账号状态仅允许0停用或1启用");
    }

    private GridOptionVO gridOption(SysGridPO grid) {
        GridOptionVO vo = new GridOptionVO();
        vo.setId(grid.getId());
        vo.setGridName(grid.getGridName());
        vo.setGridCode(grid.getGridCode());
        vo.setDistrict(grid.getDistrict());
        vo.setCommunity(grid.getCommunity());
        return vo;
    }

    private SysUserVO toVO(SysUserPO user, List<SysGridPO> grids) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setStatus(user.getStatus());
        vo.setRoleCode(BUSINESS_ROLE_CODE);
        vo.setGrids(grids.stream().map(this::gridOption).toList());
        if (grids.size() == 1) { vo.setGridId(grids.getFirst().getId()); vo.setGridName(grids.getFirst().getGridName()); }
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private void requireAdmin() {
        if (!SecurityUtils.hasRole("ADMIN")) {
            throw new BusinessException(403, "仅管理员可维护业务人员账号和网格");
        }
    }
}
