package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.ApplicationMaterial;

import java.util.List;

public interface ApplicationMaterialService extends IService<ApplicationMaterial> {

    List<ApplicationMaterial> listByApplication(Long applicationId);

    boolean verify(Long materialId, Long verifierId, boolean passed);
}