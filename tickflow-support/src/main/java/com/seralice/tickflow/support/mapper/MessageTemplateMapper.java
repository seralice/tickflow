package com.seralice.tickflow.support.mapper;

import com.seralice.tickflow.support.entity.MessageTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 放在 support 而不是 web：处理层（handler）也要查模板，它看不到 web 模块
 */
@Mapper
public interface MessageTemplateMapper {

    /** version、is_deleted、时间字段都有数据库默认值，插入时不用管 */
    @Insert("""
            INSERT INTO message_template (name, id_type, send_channel, send_account, template_type, msg_type,
                                          shield_type, msg_content, msg_status, audit_status)
            VALUES (#{name}, #{idType}, #{sendChannel}, #{sendAccount}, #{templateType}, #{msgType},
                    #{shieldType}, #{msgContent}, #{msgStatus}, #{auditStatus})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MessageTemplate template);

    /** 软删除的模板对外一律视为不存在，所以查询都带上 is_deleted = 0 */
    @Select("SELECT * FROM message_template WHERE id = #{id} AND is_deleted = 0")
    MessageTemplate findById(Long id);

    @Select("""
            <script>
            SELECT * FROM message_template
            <where>
                 is_deleted = 0
                 <if test="sendChannel != null">
                    AND send_channel = #{sendChannel}
                 </if>
                 <if test="name != null and name != ''">
                    AND name LIKE CONCAT('%', #{name}, '%')
                </if>
            </where>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{offSet}, #{size}
            </script>
            """)
    List<MessageTemplate> findPage(
            @Param("name") String name,
            @Param("offSet") int offSet,
            @Param("sendChannel") Integer sendChannel,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM message_template
            <where>
                 is_deleted = 0
                 <if test="sendChannel != null">
                    AND send_channel = #{sendChannel}
                 </if>
                 <if test="name != null and name != ''">
                    AND name LIKE CONCAT('%', #{name}, '%')
                </if>
            </where>
            </script>
            """)
    long countPage(
            @Param("name") String name,
            @Param("sendChannel") Integer sendChannel);

    @Update("""
            UPDATE message_template
            SET name = #{name},
                id_type = #{idType},
                send_channel = #{sendChannel},
                send_account = #{sendAccount},
                template_type = #{templateType},
                msg_type = #{msgType},
                shield_type = #{shieldType},
                msg_content = #{msgContent},
                audit_status = #{auditStatus},
                audit_reason = NULL,
                version = version + 1
            WHERE id = #{id} AND is_deleted = 0
            """)
    int update(MessageTemplate template);

    @Update("""
            UPDATE message_template
            SET is_deleted = 1
            WHERE id = #{id} AND is_deleted = 0
            """)
    int delete(Long id);

    @Select("SELECT COUNT(*) FROM message_template WHERE send_account = #{accountId} AND is_deleted = 0")
    int countBySendAccount(Long accountId);

    @Update("""
            UPDATE message_template
            SET audit_status = #{auditStatus},
                audit_reason = #{auditReason}
            WHERE id = #{id} AND version = #{version} AND is_deleted = 0
            """)
    int updateAuditStatusWithVersion(
            @Param("id") Long id,
            @Param("version") Integer version,
            @Param("auditStatus") Integer auditStatus,
            @Param("auditReason") String auditReason);
    @Update("""
            UPDATE message_template
            SET audit_status = 10,
                audit_reason = null,
                version = version + 1
            WHERE id = #{id}
                AND is_deleted = 0
                AND msg_type = 20
                AND audit_status = 30
            """)
    int retryAudit(Long id);

    @Update("""
            UPDATE message_template
            SET audit_status = #{auditStatus},
                audit_reason = #{auditReason},
                version = version + 1
            WHERE id = #{id}
                AND is_deleted = 0
                AND msg_type = 20
            """)
    int manual(@Param("id")Long id,
               @Param("auditReason") String auditReason,
               @Param("auditStatus") Integer auditStatus);

    @Update("""
            UPDATE message_template
            SET audit_status = #{auditStatus},
                audit_reason = #{auditReason}
            WHERE id = #{id} AND is_deleted = 0  AND audit_status = 10
            """)
    int rejectIfWaiting(
            @Param("id") Long templateId,
            @Param("auditStatus") Integer auditStatus,
            @Param("auditReason") String auditReason);
}
