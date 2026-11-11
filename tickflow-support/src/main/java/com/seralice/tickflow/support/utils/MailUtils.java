package com.seralice.tickflow.support.utils;

import com.seralice.tickflow.common.exception.SendRetryableException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * 邮件发送底层基础设施工具类
 */
@Slf4j
public class MailUtils {

    /**
     * 发送真实邮件
     *
     * @param accountConfig 发信账号配置（包含 host, port, username, password, from 等）
     * @param receivers     接收邮箱列表
     * @param title         邮件标题
     * @param content       邮件正文内容（支持 HTML）
     * @return true 发送成功；false 发送失败
     */
    public static boolean send(Map<String, Object> accountConfig, Set<String> receivers, String title, String content) {
        if (accountConfig == null || receivers == null || receivers.isEmpty()) {
            log.warn("[MailUtils] 发送邮件缺少必要参数！");
            return false;
        }

        try {
            // 1. 动态构造 Spring 邮件发送器（每个发件账号使用各自独立的 SMTP 连接配置）
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost((String) accountConfig.get("host"));
            Object portObj = accountConfig.get("port");
            int port = portObj instanceof Number ? ((Number) portObj).intValue() : Integer.parseInt(portObj.toString());
            sender.setPort(port);
            sender.setUsername((String) accountConfig.get("username"));
            sender.setPassword((String) accountConfig.get("password"));
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

            // 2. 配置 SSL / TLS 传输属性
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.timeout", "5000");

            // 3. 构建 MimeMessage 邮件对象
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            String from = (String) accountConfig.get("from");
            if (from == null || from.isBlank()) {
                from = (String) accountConfig.get("username");
            }
            helper.setFrom(from);
            helper.setTo(receivers.toArray(new String[0]));
            helper.setSubject(title != null ? title : "TickFlow 消息通知");
            helper.setText(content != null ? content : "", true);

            // 4. 执行发送
            sender.send(message);
            log.info("[MailUtils] 邮件发送成功！receivers: {}, title: {}", receivers, title);
            return true;
        } catch (MailAuthenticationException e) {
            log.error("[MailUtils] SMTP 认证失败，请检查账号或授权码: username: {}", accountConfig.get("username"), e);
            return false;
        } catch (MailSendException e) {
            log.warn("[MailUtils] 邮件发送失败（连接或投递异常）: receivers: {}, title: {}", receivers, title, e);
            throw new SendRetryableException("邮件发送失败，receivers:" + receivers,e);
        } catch (Exception e) {
            log.error("[MailUtils] 邮件发送异常！receivers: {}, title: {}", receivers, title, e);
            return false;
        }
    }
}
