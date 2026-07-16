package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 人口-身份证影印本 Mapper。 */
@Mapper
public interface PersonIdCardImageMapper {

    /** 草稿式插入：未关联 person，ocr_status='SKIPPED'。 */
    int insertDraft(PersonIdCardImage row);

    Optional<PersonIdCardImage> selectById(@Param("imageId") Long imageId);

    Optional<PersonIdCardImage> selectBySha256(@Param("fileSha256") String fileSha256);

    Optional<PersonIdCardImage> selectByPersonId(@Param("personId") Long personId);

    int markOcrSuccess(@Param("imageId") Long imageId,
                       @Param("ocrProvider") String provider,
                       @Param("ocrEngineVersion") String engineVersion,
                       @Param("elapsedMs") Integer elapsedMs,
                       @Param("confidence") Double confidence,
                       @Param("idCardFull") String idCardFull,
                       @Param("idCardMasked") String idCardMasked,
                       @Param("name") String name,
                       @Param("birthDate") LocalDate birthDate,
                       @Param("gender") String gender,
                       @Param("ethnicity") String ethnicity,
                       @Param("address") String address,
                       @Param("rawJson") String rawJson);

    int markOcrFailed(@Param("imageId") Long imageId,
                      @Param("ocrProvider") String provider,
                      @Param("ocrEngineVersion") String engineVersion,
                      @Param("elapsedMs") Integer elapsedMs,
                      @Param("error") String error);

    int markOcrSkipped(@Param("imageId") Long imageId);

    /** 仅在 image 尚未绑定 person 时绑定。 */
    int bindPerson(@Param("imageId") Long imageId, @Param("personId") Long personId);
}
