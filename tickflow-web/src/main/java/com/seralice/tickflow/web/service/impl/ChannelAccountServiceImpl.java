package com.seralice.tickflow.web.service.impl;

import com.seralice.tickflow.common.enums.ChannelType;
import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.utils.EnumUtils;
import com.seralice.tickflow.support.entity.ChannelAccount;
import com.seralice.tickflow.support.mapper.ChannelAccountMapper;
import com.seralice.tickflow.support.mapper.MessageTemplateMapper;
import com.seralice.tickflow.web.dto.ChannelAccountRequest;
import com.seralice.tickflow.web.service.ChannelAccountService;
import com.seralice.tickflow.web.vo.ChannelAccountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChannelAccountServiceImpl implements ChannelAccountService {

    private final ChannelAccountMapper channelAccountMapper;
    private final JsonMapper jsonMapper;
    private final MessageTemplateMapper messageTemplateMapper;


    @Override
    public Long create(ChannelAccountRequest req) {
        checkRequest(req);
        ChannelAccount channelAccount = new ChannelAccount();
        BeanUtils.copyProperties(req, channelAccount);
        channelAccount.setAccountConfig(jsonMapper.writeValueAsString(req.getAccountConfig()));

        channelAccountMapper.insert(channelAccount);

        return channelAccount.getId();
    }

    @Override
    public List<ChannelAccountVO> list(Integer sendChannel) {
        List<ChannelAccount> accounts = channelAccountMapper.list(sendChannel);

        List<ChannelAccountVO> voList = new ArrayList<>();

        for(ChannelAccount account : accounts) {
            ChannelAccountVO vo = new ChannelAccountVO();
            BeanUtils.copyProperties(account, vo);

            Map<String, Object> config = jsonMapper.readValue(account.getAccountConfig(), new TypeReference<>() {
            });

            if( config.containsKey("password")){
                config.put("password", "******");
            }

            if( config.get("webhook") instanceof String webhook){
                if (webhook.length() > 40) {
                    config.put("webhook", webhook.substring(0, 40) + "****");
                }
            }
            vo.setAccountConfig(config);

            voList.add(vo);
        }

        return voList;

    }

    @Override
    public void update(Long id, ChannelAccountRequest req) {
        ChannelAccount old = channelAccountMapper.findById(id);

        if( old == null){
            throw new BizException(404, "账号不存在");
        }

        if( !old.getSendChannel().equals(req.getSendChannel())){
            throw new BizException(400,"sendChannel 不允许修改");
        }
        Map<String, Object> oldConfig = jsonMapper.readValue(old.getAccountConfig(), new TypeReference<>() {});

        for (String sensitiveKey : List.of("password", "webhook")) {
            Object val = req.getAccountConfig().get(sensitiveKey);
            if (val == null || (val instanceof String s && s.isBlank())) {
                if (oldConfig.containsKey(sensitiveKey)) {
                    req.getAccountConfig().put(sensitiveKey, oldConfig.get(sensitiveKey));
                }
            }
        }

        checkRequest(req);

        ChannelAccount account = new ChannelAccount();
        account.setId(id);
        account.setName(req.getName());
        account.setAccountConfig(jsonMapper.writeValueAsString(req.getAccountConfig()));


        int rows = channelAccountMapper.update(account);
        if (rows == 0) {
            throw new BizException(404, "账号不存在");
        }


    }

    @Override
    public void delete(Long id) {
        int count = messageTemplateMapper.countBySendAccount(id);
        if( count > 0){
            throw new BizException(409,"仍有"+count+"个模板在使用该账户");
        }

        int row = channelAccountMapper.delete(id);
        if( row == 0){
            throw new BizException(404, "账户不存在");
        }

    }


    private void checkRequest(ChannelAccountRequest req) {
        // 1. 数字转枚举，合法性校验（不是 40/110/900 自动抛 400）
        ChannelType channel = EnumUtils.require(ChannelType.class, req.getSendChannel(), "sendChannel");

        // 2. 根据渠道动态校验 accountConfig 里的必填项
        for (String field : channel.getAccountFields()) {
            Object value = req.getAccountConfig().get(field);
            if (value == null || (value instanceof String s && s.isBlank())) {
                throw new BizException(400, "accountConfig." + field + " 不能为空");
            }
        }
    }
}
