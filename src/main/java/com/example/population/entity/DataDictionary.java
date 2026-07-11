package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("data_dictionary")
public class DataDictionary implements Serializable {

    @TableId(value = "dict_id", type = IdType.AUTO)
    private Long dictId;

    private String dictType;

    private String dictCode;

    private String dictLabel;

    private Integer sortNo;

    private String status;

    private String remark;
}