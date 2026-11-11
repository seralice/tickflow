package com.seralice.tickflow.common.pipeline;

import lombok.Data;

import java.util.List;

/**
 * 责任链执行模板：保存某条业务线按序执行的工位列表
 */
@Data
public class ProcessTemplate {

    private List<BusinessProcess<?>> processList;
}
