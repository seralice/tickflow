package com.seralice.tickflow.web.advice;

import com.seralice.tickflow.common.exception.BizException;
import com.seralice.tickflow.common.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：Controller 抛出的异常都在这里统一转成 Result，避免把堆栈直接返回给调用方
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** @Valid 参数校验失败时触发，取第一个出错字段的提示返回 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        var error = e.getBindingResult().getFieldErrors().getFirst();
        return Result.fail(400, error.getField() + " " + error.getDefaultMessage());
    }

    /** 兜底：未预料的异常记录日志，只给调用方返回笼统提示 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理的异常", e);
        return Result.fail(500, "服务器内部错误");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleBadRequest(MethodArgumentTypeMismatchException e){
        log.warn("参数类型错误:{} = {}",e.getName(),e.getValue());
        return  Result.fail(400,e.getName() + "格式不正确");
    }

}
