package com.seralice.tickflow.web.service;

import com.seralice.tickflow.common.vo.PageResult;
import com.seralice.tickflow.web.dto.MessageTemplateRequest;
import com.seralice.tickflow.web.dto.TemplateManualAuditRequest;
import com.seralice.tickflow.web.vo.MessageTemplateVO;
import com.seralice.tickflow.web.vo.TemplateAuditVO;
import jakarta.validation.Valid;

public interface MessageTemplateService {

    /** 创建模板，返回新模板 id */
    Long create(MessageTemplateRequest req);

    /** 查询单个模板，不存在或已删除时抛 404 */
    MessageTemplateVO getById(Long id);

    PageResult<MessageTemplateVO> list(int page, int size, String name, Integer sendChannel);

    void update(Long id, MessageTemplateRequest req);

    void delete(Long id);

    Long copy(Long id);

    TemplateAuditVO getAuditById(Long id);

    void retryAudit(Long id);

    void manual(Long id, @Valid TemplateManualAuditRequest req);
}
