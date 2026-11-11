-- 枚举字段沿用 Austin 的整数编码（10 / 20 / 30 ...），取值含义见 docs/API.md「枚举」一节

CREATE TABLE IF NOT EXISTS channel_account (
    id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL COMMENT '账号名称',
    send_channel   INT          NOT NULL COMMENT '发送渠道：40 邮件 / 110 飞书机器人 / 900 模拟渠道',
    account_config JSON         NOT NULL COMMENT '账号配置，结构随渠道变化（SMTP 配置、webhook 等）',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_send_channel (send_channel)
) COMMENT '渠道账号';

CREATE TABLE IF NOT EXISTS message_template (
    id               BIGINT        PRIMARY KEY AUTO_INCREMENT,
    name             VARCHAR(100)  NOT NULL COMMENT '模板名称',
    id_type          TINYINT       NOT NULL COMMENT '接收者 id 类型：10 用户 id / 50 邮箱',
    send_channel     INT           NOT NULL COMMENT '发送渠道：40 邮件 / 110 飞书机器人 / 900 模拟渠道',
    send_account     BIGINT        NOT NULL COMMENT '渠道账号，对应 channel_account.id',
    template_type    TINYINT       NOT NULL COMMENT '10 定时类 / 20 实时类',
    msg_type         TINYINT       NOT NULL COMMENT '10 通知 / 20 营销 / 30 验证码',
    shield_type      TINYINT       NOT NULL COMMENT '10 夜间不屏蔽 / 20 夜间屏蔽 / 30 夜间屏蔽且次日 9 点发送',
    msg_content      JSON          NOT NULL COMMENT '消息内容，结构随渠道变化，占位符写作 {$var}',
    msg_status       TINYINT       NOT NULL COMMENT '10 新建 / 20 停用 / 30 启用，只对定时类有意义',
    audit_status     TINYINT       NOT NULL COMMENT '10 待审核 / 20 审核通过 / 30 审核驳回',
    audit_reason     VARCHAR(1000)          COMMENT '审核意见（AI 初审或人工复核）',
    expect_push_time VARCHAR(100)           COMMENT '定时类的 cron 表达式',
    cron_crowd_path  VARCHAR(500)           COMMENT '定时类的人群文件路径',
    version          INT           NOT NULL DEFAULT 0 COMMENT '内容版本号，每次修改 +1，防止旧的审核结果覆盖新内容',
    is_deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '0 未删除 / 1 已删除',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_send_channel (send_channel)
) COMMENT '消息模板';
