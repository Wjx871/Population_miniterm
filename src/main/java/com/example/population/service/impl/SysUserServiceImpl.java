package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.LoginDTO;
import com.example.population.entity.SysUser;
import com.example.population.mapper.SysUserMapper;
import com.example.population.service.SysUserService;
import com.example.population.util.JwtUtil;
import com.example.population.util.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JwtUtil jwtUtil;

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = getByUsername(dto.getUsername());
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!PasswordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("账号已停用");
        }
        user.setLastLoginAt(LocalDateTime.now());
        this.updateById(user);

        String token = jwtUtil.generate(user.getUserId(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("roleId", user.getRoleId());
        result.put("departmentId", user.getDepartmentId());
        return result;
    }

    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    @Override
    public IPage<SysUser> page(long current, long size, String keyword, Long roleId, Long departmentId) {
        Page<SysUser> page = new Page<>(current, size);
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword);
        }
        if (roleId != null) {
            w.eq(SysUser::getRoleId, roleId);
        }
        if (departmentId != null) {
            w.eq(SysUser::getDepartmentId, departmentId);
        }
        w.orderByDesc(SysUser::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public boolean resetPassword(Long userId, String newPassword) {
        SysUser user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setPasswordHash(PasswordEncoder.encode(newPassword));
        return this.updateById(user);
    }
}