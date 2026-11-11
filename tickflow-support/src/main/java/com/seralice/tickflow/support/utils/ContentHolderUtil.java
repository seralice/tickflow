package com.seralice.tickflow.support.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符替换工具类：将文本中的 {$变量名} 替换为对应 Map 中的值，并支持提取占位符列表
 */
public class ContentHolderUtil {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\$([a-zA-Z0-9_]+)\\}");

    private ContentHolderUtil() {
    }

    /**
     * 提取文本中的所有占位符变量名列表
     *
     * @param content 文本内容
     * @return 变量名列表，例如 ["name", "code"]
     */
    public static List<String> extractPlaceHolderKeys(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * 替换模板内容中的占位符
     *
     * @param content   原始模板文本，如 "你好 {$name}，验证码为 {$code}"
     * @param variables 变量字典，如 {"name": "小明", "code": "123456"}
     * @return 渲染后的文本
     */
    public static String replacePlaceHolder(String content, Map<String, String> variables) {
        if (content == null || content.isBlank() || variables == null || variables.isEmpty()) {
            return content;
        }

        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.get(variableName);
            // 若未找到变量则保留原占位符
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
