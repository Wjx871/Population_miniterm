package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.LoginDTO;
import com.example.population.entity.SysUser;

import java.util.Map;

public interface SysUserService extends IService<SysUser> {

    Map<String, Object> login(LoginDTO dto);

    SysUser getByUsername(String username);

    IPage<SysUser> page(long current, long size, String keyword, Long roleId, Long departmentId);

    boolean resetPassword(Long userId, String newPassword);
}