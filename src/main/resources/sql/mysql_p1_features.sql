USE ticket_management;

-- P1 增量升级：工作流暂停与重开、知识库、快捷回复、处理宏和数据生命周期记录。
-- 脚本可重复执行，不删除现有工单。执行前请备份。

DROP PROCEDURE IF EXISTS add_p1_order_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_p1_order_column_if_missing(IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE orders ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE p1_statement FROM @ddl;
        EXECUTE p1_statement;
        DEALLOCATE PREPARE p1_statement;
    END IF;
END $$
DELIMITER ;

CALL add_p1_order_column_if_missing('sla_paused_at', 'DATETIME(3) NULL AFTER sla_state');
CALL add_p1_order_column_if_missing('sla_pause_reason', 'VARCHAR(100) NULL AFTER sla_paused_at');
CALL add_p1_order_column_if_missing('total_sla_paused_minutes', 'INT NOT NULL DEFAULT 0 AFTER sla_pause_reason');
CALL add_p1_order_column_if_missing('reopen_deadline_at', 'DATETIME(3) NULL AFTER total_sla_paused_minutes');
CALL add_p1_order_column_if_missing('reopen_count', 'INT NOT NULL DEFAULT 0 AFTER reopen_deadline_at');
DROP PROCEDURE add_p1_order_column_if_missing;

-- 2026-07 范围调整：不再提供高级搜索保存视图与 MFA，清理旧升级遗留表。
DROP TABLE IF EXISTS saved_ticket_views;
DROP TABLE IF EXISTS user_mfa;

CREATE TABLE IF NOT EXISTS knowledge_articles (
    article_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    category_id BIGINT NULL,
    keywords VARCHAR(500) NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_knowledge_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    CONSTRAINT fk_knowledge_creator FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT fk_knowledge_updater FOREIGN KEY (updated_by) REFERENCES users(user_id),
    INDEX idx_knowledge_status_category (status, category_id, updated_at),
    FULLTEXT INDEX ft_knowledge_text (title, summary, content, keywords)
);

CREATE TABLE IF NOT EXISTS reply_templates (
    template_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_name VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    category_id BIGINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_reply_template_category FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    CONSTRAINT fk_reply_template_creator FOREIGN KEY (created_by) REFERENCES users(user_id),
    INDEX idx_reply_templates_enabled_category (enabled, category_id, template_name)
);

CREATE TABLE IF NOT EXISTS handling_macros (
    macro_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    macro_name VARCHAR(120) NOT NULL,
    reply_template_id BIGINT NULL,
    target_status TINYINT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_macro_template FOREIGN KEY (reply_template_id) REFERENCES reply_templates(template_id) ON DELETE SET NULL,
    CONSTRAINT fk_macro_creator FOREIGN KEY (created_by) REFERENCES users(user_id),
    INDEX idx_handling_macros_enabled (enabled, macro_name)
);

CREATE TABLE IF NOT EXISTS data_lifecycle_runs (
    run_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_type VARCHAR(40) NOT NULL,
    cutoff_at DATETIME(3) NULL,
    affected_count BIGINT NOT NULL DEFAULT 0,
    artifact_path VARCHAR(500) NULL,
    artifact_checksum CHAR(64) NULL,
    result_status ENUM('SUCCESS', 'FAILED') NOT NULL,
    result_message VARCHAR(1000) NULL,
    performed_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_lifecycle_run_user FOREIGN KEY (performed_by) REFERENCES users(user_id),
    INDEX idx_lifecycle_type_time (run_type, created_at)
);

SET @p1_seed_actor = (
    SELECT user_id FROM users
    WHERE role IN ('ADMIN', 'ROOT') AND status = 1
    ORDER BY CASE WHEN role = 'ADMIN' THEN 0 ELSE 1 END, user_id
    LIMIT 1
);

DROP PROCEDURE IF EXISTS seed_p1_article;
DROP PROCEDURE IF EXISTS seed_p1_template;
DROP PROCEDURE IF EXISTS seed_p1_macro;
DELIMITER $$
CREATE PROCEDURE seed_p1_article(
    IN p_title VARCHAR(200), IN p_summary VARCHAR(500), IN p_content TEXT,
    IN p_category_id BIGINT, IN p_keywords VARCHAR(500)
)
BEGIN
    DECLARE v_category_id BIGINT DEFAULT NULL;
    IF p_category_id IS NULL
       OR EXISTS (SELECT 1 FROM categories WHERE category_id = p_category_id) THEN
        SET v_category_id = p_category_id;
    END IF;
    IF @p1_seed_actor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM knowledge_articles WHERE title = p_title) THEN
        INSERT INTO knowledge_articles
            (title, summary, content, category_id, keywords, status, created_by, updated_by)
        VALUES (p_title, p_summary, p_content, v_category_id, p_keywords,
                'PUBLISHED', @p1_seed_actor, @p1_seed_actor);
    END IF;
END $$

CREATE PROCEDURE seed_p1_template(
    IN p_name VARCHAR(120), IN p_content TEXT, IN p_category_id BIGINT
)
BEGIN
    DECLARE v_category_id BIGINT DEFAULT NULL;
    IF p_category_id IS NULL
       OR EXISTS (SELECT 1 FROM categories WHERE category_id = p_category_id) THEN
        SET v_category_id = p_category_id;
    END IF;
    IF @p1_seed_actor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM reply_templates WHERE template_name = p_name) THEN
        INSERT INTO reply_templates (template_name, content, category_id, enabled, created_by)
        VALUES (p_name, p_content, v_category_id, 1, @p1_seed_actor);
    END IF;
END $$

CREATE PROCEDURE seed_p1_macro(
    IN p_name VARCHAR(120), IN p_template_name VARCHAR(120), IN p_target_status TINYINT
)
BEGIN
    IF @p1_seed_actor IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM handling_macros WHERE macro_name = p_name) THEN
        INSERT INTO handling_macros
            (macro_name, reply_template_id, target_status, enabled, created_by)
        SELECT p_name, template_id, p_target_status, 1, @p1_seed_actor
        FROM reply_templates
        WHERE template_name = p_template_name
        ORDER BY template_id
        LIMIT 1;
    END IF;
END $$
DELIMITER ;

CALL seed_p1_article('如何补充工单信息', '提交复现步骤、截图和发生时间可以加快处理。',
    '请提供：1. 问题发生时间；2. 完整复现步骤；3. 错误提示或截图；4. 期望结果。', NULL,
    '复现步骤,截图,错误信息');
CALL seed_p1_article('账号密码正确但无法登录', '排查账号锁定、密码过期和客户端缓存。',
    '先确认用户名无多余空格并重新输入密码。若连续失败多次，请等待账号保护期结束；仍无法登录时清理客户端缓存，并在工单中提供登录时间和错误提示。', 4005,
    '登录失败,账号锁定,密码错误');
CALL seed_p1_article('收不到短信或邮件验证码', '从发送频率、拦截设置和联系方式三方面排查。',
    '请等待 60 秒后重试，检查垃圾邮件、短信拦截和手机信号，并确认账号绑定的邮箱或手机号正确。短时间内请勿连续点击，以免触发发送限制。', 4005,
    '验证码,短信,邮件,收不到');
CALL seed_p1_article('实名认证失败的常见原因', '姓名证件不一致、图片不清晰或证件过期都会导致失败。',
    '请使用有效期内证件原件拍摄，保证四角完整、文字清晰、无反光遮挡；填写姓名和证件号码时与证件完全一致，提交后不要重复修改资料。', 4006,
    '实名认证,证件,审核失败');
CALL seed_p1_article('实名认证审核需要多久', '说明正常审核时间和加急所需信息。',
    '实名认证通常在 1 至 3 个工作日内完成。超过 3 个工作日仍未更新时，请提交申请时间、账号和申请页面截图，客服会核对审核队列。', 4006,
    '实名认证,审核时间,进度');
CALL seed_p1_article('如何申请订单退款', '介绍退款入口、范围和处理步骤。',
    '进入订单详情选择申请退款，填写原因并提交。已发货、已核销或超过售后期的订单可能需要人工审核；请保留订单号和相关凭证。', 4007,
    '退款申请,订单,售后');
CALL seed_p1_article('退款成功但款项未到账', '不同支付渠道到账时间不同。',
    '退款状态显示成功后，银行卡通常需 1 至 7 个工作日，余额或原支付账户一般更快。超过渠道时限请提供订单号、退款时间和支付流水号。', 4007,
    '退款未到账,支付渠道,流水号');
CALL seed_p1_article('发现重复扣费怎么办', '先确认是否存在多笔订单或预授权，再提交支付凭证。',
    '请核对银行账单中的商户名、金额和交易时间，并确认是否生成了两笔订单。若仅一笔订单但发生两次实际扣款，请上传两笔支付流水截图，注意遮挡完整卡号。', 4008,
    '重复扣费,账单,支付流水');
CALL seed_p1_article('支付成功但订单状态未更新', '避免重复支付并帮助客服定位异步通知。',
    '请勿再次付款。先刷新订单页并等待 10 分钟；仍显示未支付时，提供订单号、支付时间、金额和渠道流水号，客服将核对支付回调。', 4002,
    '支付成功,订单未更新,支付回调');
CALL seed_p1_article('支付失败的自助排查', '检查限额、网络、余额和渠道维护状态。',
    '请确认账户余额和单笔限额充足，关闭代理网络后重试，并检查银行或支付渠道是否处于维护中。失败后请记录错误码，不要连续发起多笔相同支付。', 4002,
    '支付失败,限额,错误码');
CALL seed_p1_article('页面出现 500 或系统错误', '记录发生时间和请求路径便于定位服务端异常。',
    '刷新一次并重新登录；若仍报错，请提供发生时间、页面地址、操作步骤和完整错误截图。请勿在截图中暴露密码、验证码等敏感信息。', 4009,
    '500错误,页面报错,服务异常');
CALL seed_p1_article('页面空白或一直加载', '排查浏览器缓存、扩展和网络环境。',
    '尝试无痕窗口、清理本站缓存并禁用脚本拦截类扩展，然后切换网络重试。若问题持续，请提供浏览器版本、页面地址和开发者工具中的错误截图。', 4009,
    '页面空白,加载中,缓存,浏览器');
CALL seed_p1_article('导出任务失败或文件为空', '缩小时间范围并检查下载权限。',
    '先将导出时间范围缩小到 30 天内并减少筛选条件，确认浏览器允许下载。若文件仍为空，请提供导出时间、筛选条件和任务提示。', 4010,
    '导出失败,空文件,下载');
CALL seed_p1_article('附件上传或预览失败', '说明支持的文件大小和重新上传方法。',
    '单个附件不超过 10 MB，一次最多 5 个且总计不超过 25 MB。请缩短文件名、确认文件未损坏后重新上传；预览失败时可先另存到本地打开。', 4010,
    '附件,上传失败,预览失败');
CALL seed_p1_article('收不到工单回复通知', '检查站内未读消息和通知偏好。',
    '先打开系统通知中心查看未读消息，再检查个人资料中的通知偏好和邮箱地址。邮件可能被归入垃圾邮件；即使邮件延迟，站内工单回复仍会完整保留。', 4004,
    '通知,邮件,工单回复');
CALL seed_p1_article('订单金额或状态显示异常', '先核对订单详情、优惠和实际支付记录。',
    '请刷新订单详情并核对商品金额、优惠、退款和实际支付记录。提交工单时提供订单号、期望金额、当前显示值及对应截图。', 4004,
    '订单金额,状态异常,优惠');
CALL seed_p1_article('支付后没有生成发票', '说明开票条件和人工核对信息。',
    '确认订单已完成且填写了正确的发票抬头和税号。开票任务通常需要一定处理时间；超过页面承诺时限后，请提供订单号、开票时间和发票抬头。', 4004,
    '发票,开票,订单');
CALL seed_p1_article('客户端升级与缓存清理指南', '升级前保存工作，升级后清理旧缓存。',
    '退出客户端后安装最新版本，再重新登录。若升级后界面异常，可在设置中清理缓存并重启；重要附件和未提交内容请先保存到本地。', 4003,
    '客户端升级,缓存,重启');
CALL seed_p1_article('系统维护期间如何处理', '解释维护窗口、暂挂工单和恢复后的处理顺序。',
    '计划维护期间部分功能可能暂时不可用，相关工单会被暂挂并暂停 SLA。维护结束后系统恢复计时，客服会按原优先级继续处理。', 4003,
    '系统维护,暂挂,SLA');
CALL seed_p1_article('提交截图和日志时的安全提示', '上传前遮挡密码、验证码、证件号和银行卡号。',
    '仅提供定位问题所需的信息。截图和日志中请遮挡密码、动态验证码、完整证件号码、银行卡号和访问令牌；客服不会索要账号密码。', 4001,
    '隐私,截图,日志,敏感信息');

CALL seed_p1_template('请客户补充信息', '您好，为了继续处理，请补充问题发生时间、完整复现步骤以及相关截图。', NULL);
CALL seed_p1_template('已收到并开始处理', '您好，您的问题我们已经收到，客服正在核对相关记录。后续进展会通过工单回复通知您，请暂时不要重复提交。', NULL);
CALL seed_p1_template('方案已提供请确认', '您好，处理方案已在本工单中提供，请按步骤操作并告知结果。若问题已解决，也请确认关闭工单，谢谢。', NULL);
CALL seed_p1_template('登录异常排查', '您好，请确认用户名无多余空格并重新输入密码；若连续失败，请等待账号保护期结束后再试，并提供登录时间和错误提示。', 4005);
CALL seed_p1_template('验证码接收排查', '您好，请等待 60 秒后重试，并检查垃圾邮件、短信拦截和账号绑定的联系方式。若仍未收到，请提供发送时间及脱敏后的手机号或邮箱。', 4005);
CALL seed_p1_template('实名认证补充资料', '您好，请重新上传有效期内的证件原件照片，确保四角完整、文字清晰且无反光，并确认姓名和证件号码完全一致。', 4006);
CALL seed_p1_template('实名认证审核中', '您好，您的实名认证资料已进入人工审核，预计 1 至 3 个工作日完成。审核期间请勿重复提交或修改资料。', 4006);
CALL seed_p1_template('退款申请已受理', '您好，退款申请已受理，我们正在核对订单和支付记录。审核结果及原路退回进度会在本工单中同步。', 4007);
CALL seed_p1_template('退款到账时限说明', '您好，系统已完成退款操作，实际到账时间以支付渠道为准，银行卡通常需要 1 至 7 个工作日。超过时限请回复支付流水号。', 4007);
CALL seed_p1_template('重复扣费核实', '您好，请提供两笔扣款的交易时间、金额和脱敏后的支付流水截图。请遮挡完整卡号和其他无关敏感信息。', 4008);
CALL seed_p1_template('请提供支付凭证', '您好，请提供订单号、支付时间、金额、支付渠道和脱敏后的渠道流水号。核实前请勿重复支付。', 4002);
CALL seed_p1_template('订单状态同步中', '您好，已确认支付记录，正在同步订单状态。同步完成后页面会自动更新，请稍后刷新查看。', 4004);
CALL seed_p1_template('请补充页面报错信息', '您好，请补充报错发生时间、页面地址、完整操作步骤、浏览器或客户端版本以及错误截图。', 4009);
CALL seed_p1_template('建议清理缓存后重试', '您好，请退出当前页面，清理本站缓存或客户端缓存后重新登录再试。如仍异常，请告知操作结果。', 4009);
CALL seed_p1_template('导出替代方案', '您好，请先将导出时间范围缩小到 30 天内并减少筛选条件后重试。若仍失败，请回复导出时间和筛选条件。', 4010);
CALL seed_p1_template('附件重新上传', '您好，请确认单个附件不超过 10 MB、文件未损坏并缩短文件名后重新上传；也可先压缩文件再提交。', 4010);
CALL seed_p1_template('通知设置检查', '您好，请先查看站内通知中心，并检查个人资料中的通知偏好、邮箱地址及垃圾邮件目录。', 4004);
CALL seed_p1_template('计划维护通知', '您好，当前功能处于计划维护窗口，相关工单已暂挂并暂停 SLA。维护结束后我们会恢复处理并通知您。', 4003);
CALL seed_p1_template('问题已解决请确认', '您好，问题已处理完成，请您验证业务结果。若确认无误可关闭工单；如仍有异常，请在可重新打开期限内回复。', NULL);
CALL seed_p1_template('暂时无法复现', '您好，目前根据已有信息暂时无法复现问题。工单将先行暂挂，请补充最新复现时间、步骤和相关截图后我们继续排查。', NULL);

CALL seed_p1_macro('请补充并等待客户', '请客户补充信息', 5);
CALL seed_p1_macro('确认受理并开始处理', '已收到并开始处理', 1);
CALL seed_p1_macro('方案已提供等待确认', '方案已提供请确认', 5);
CALL seed_p1_macro('登录排查等待客户', '登录异常排查', 5);
CALL seed_p1_macro('验证码排查等待客户', '验证码接收排查', 5);
CALL seed_p1_macro('实名认证补件等待客户', '实名认证补充资料', 5);
CALL seed_p1_macro('实名认证审核暂挂', '实名认证审核中', 6);
CALL seed_p1_macro('退款受理进入处理中', '退款申请已受理', 1);
CALL seed_p1_macro('退款到账说明待确认', '退款到账时限说明', 5);
CALL seed_p1_macro('重复扣费进入核查', '重复扣费核实', 1);
CALL seed_p1_macro('支付凭证待补充', '请提供支付凭证', 5);
CALL seed_p1_macro('订单同步进入处理中', '订单状态同步中', 1);
CALL seed_p1_macro('页面报错待补充', '请补充页面报错信息', 5);
CALL seed_p1_macro('缓存方案等待验证', '建议清理缓存后重试', 5);
CALL seed_p1_macro('导出替代方案待确认', '导出替代方案', 5);
CALL seed_p1_macro('附件重传等待客户', '附件重新上传', 5);
CALL seed_p1_macro('通知设置等待验证', '通知设置检查', 5);
CALL seed_p1_macro('维护窗口暂挂工单', '计划维护通知', 6);
CALL seed_p1_macro('客户确认后完成', '问题已解决请确认', 2);
CALL seed_p1_macro('无法复现暂挂', '暂时无法复现', 6);

DROP PROCEDURE seed_p1_article;
DROP PROCEDURE seed_p1_template;
DROP PROCEDURE seed_p1_macro;
