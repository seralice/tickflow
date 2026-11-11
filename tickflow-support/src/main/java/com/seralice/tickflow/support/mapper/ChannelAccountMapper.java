package com.seralice.tickflow.support.mapper;

import com.seralice.tickflow.support.entity.ChannelAccount;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChannelAccountMapper {

    @Select("SELECT * FROM channel_account WHERE id = #{id} AND is_deleted = 0")
    ChannelAccount findById(Long id);

    @Insert("""
    INSERT INTO channel_account (name, send_channel, account_config) 
    VALUES (#{name},#{sendChannel},#{accountConfig})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ChannelAccount channelAccount);

    @Select("""
    <script>
    SELECT * FROM channel_account
    <where>
         is_deleted = 0
         <if test = "sendChannel != null" >
            AND send_channel = #{sendChannel}
         </if>
    </where>
    ORDER BY updated_at DESC, id desc
    </script>
    """)
    List<ChannelAccount> list(@Param("sendChannel") Integer sendChannel);

    @Update("""
            UPDATE channel_account
                    SET name = #{name},
                        account_config = #{accountConfig}
                    WHERE id = #{id} AND is_deleted = 0
            """)
    int update(ChannelAccount account);

    @Update("UPDATE channel_account SET is_deleted = 1 WHERE id = #{id} AND is_deleted = 0")
    int delete(Long id);
}
