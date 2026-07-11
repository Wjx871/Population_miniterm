package com.example.population.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysRole;
import com.example.population.mapper.SysRoleMapper;
import com.example.population.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
}