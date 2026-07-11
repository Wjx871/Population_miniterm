package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("application_material")
public class ApplicationMaterial implements Serializable {

    @TableId(value = "material_id", type = IdType.AUTO)
    private Long materialId;

    private Long applicationId;

    private String materialTypeCode;

    private String materialName;

    private String materialNo;

    private String fileName;

    private String storageUri;

    private String fileHash;

    private Integer requiredFlag;

    private String verifyStatus;

    private Long verifiedBy;

    private LocalDateTime verifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;

    private Long uploaderUserId;
}