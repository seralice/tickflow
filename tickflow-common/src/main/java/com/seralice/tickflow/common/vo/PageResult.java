package com.seralice.tickflow.common.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 通用分页结果：放在 common，模板列表、以后的死信列表等所有分页接口都复用它
 */
@Getter
@AllArgsConstructor
public class PageResult<T> {

    /** 符合条件的总条数（不是当前页的条数），前端靠它算总页数 */
    private final long total;

    /** 当前页的数据 */
    private final List<T> list;
}
