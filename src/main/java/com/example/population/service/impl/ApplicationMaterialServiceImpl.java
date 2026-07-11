package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.ApplicationMaterial;
import com.example.population.mapper.ApplicationMaterialMapper;
import com.example.population.service.ApplicationMaterialService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApplicationMaterialServiceImpl extends ServiceImpl<ApplicationMaterialMapper, ApplicationMaterial>
        implements ApplicationMaterialService {

    @Override
    public List<ApplicationMaterial> listByApplication(Long applicationId) {
        return this.list(new LambdaQueryWrapper<ApplicationMaterial>()
                .eq(ApplicationMaterial::getApplicationId, applicationId)
                .orderByAsc(ApplicationMaterial::getMaterialId));
    }

    @Override
    public boolean verify(Long materialId, Long verifierId, boolean passed) {
        ApplicationMaterial m = this.getById(materialId);
        if (m == null) {
            return false;
        }
        m.setVerifyStatus(passed ? "VERIFIED" : "REJECTED");
        m.setVerifiedBy(verifierId);
        m.setVerifiedAt(LocalDateTime.now());
        return this.updateById(m);
    }
}