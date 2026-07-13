package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CertificateMapper extends BaseMapper<Certificate> {

    /**
     * 同类型同号码查重（事务内允许走 FOR UPDATE）。
     */
    Certificate findByTypeAndNoForUpdate(@Param("type") String certificateTypeCode,
                                         @Param("no") String certificateNo);

    /**
     * 批量将有效期已过的非 CANCELLED 证件置为 EXPIRED；返回受影响行数。
     */
    int markExpiredAsOf(@Param("today") LocalDate today);

    /**
     * 即将到期提醒列表（warnDays 内）。返回单行（仅 ID 列表）。
     */
    List<Certificate> listExpiringCertificates(@Param("warnDays") int warnDays);
}