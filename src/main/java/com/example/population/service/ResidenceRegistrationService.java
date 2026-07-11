package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.ResidenceRegisterDTO;
import com.example.population.entity.ResidenceRegistration;

public interface ResidenceRegistrationService extends IService<ResidenceRegistration> {

    ResidenceRegistration getByPerson(Long personId);

    /**
     * 当前户籍登记。事务内：FOR UPDATE 查重，违反一人一条抛 PersonAlreadyHasRegistrationException。
     */
    ResidenceRegistration register(ResidenceRegisterDTO dto);
}