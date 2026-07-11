package com.example.population.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysPermission;
import com.example.population.mapper.SysPermissionMapper;
import com.example.population.service.SysPermissionService;
import org.springframework.stereotype.Service;

@Service
public class SysPermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements SysPermissionService {
}