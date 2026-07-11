package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.SysDepartment;

public interface SysDepartmentService extends IService<SysDepartment> {

    IPage<SysDepartment> page(long current, long size, String keyword, String regionCode, Long parentId);
}