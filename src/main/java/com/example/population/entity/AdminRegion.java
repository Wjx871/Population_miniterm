package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("admin_region")
public class AdminRegion implements Serializable {

    @TableId(value = "region_code", type = IdType.INPUT)
    private String regionCode;

    private String regionName;

    private String parentCode;

    private String regionLevelCode;

    private String cityCode;

    private Integer enabledFlag;

    private Integer sortNo;

    @TableField(exist = false)
    private String parentName;
}