package com.ticket.dao.mysql;

import com.ticket.dao.BaseDAO;
import com.ticket.model.HandlingMacro;
import com.ticket.model.KnowledgeArticle;
import com.ticket.model.ReplyTemplate;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

public class KnowledgeDAO extends BaseDAO {
    public List<KnowledgeArticle> searchPublished(Long categoryId, String keyword, int limit) {
        String text = keyword == null ? "" : keyword.trim();
        boolean hasCategory = categoryId != null;
        boolean hasText = !text.isBlank();
        String sql = "SELECT * FROM knowledge_articles WHERE status = 'PUBLISHED'"
            + (hasCategory ? " AND (category_id = ? OR category_id IS NULL)" : "")
            + (hasText ? " AND (title LIKE ? OR summary LIKE ? OR keywords LIKE ? OR content LIKE ?)" : "")
            + " ORDER BY (category_id IS NULL), updated_at DESC LIMIT ?";
        return query(sql, statement -> {
            int index = 1;
            if (hasCategory) statement.setLong(index++, categoryId);
            if (hasText) {
                String pattern = "%" + text + "%";
                for (int i = 0; i < 4; i++) statement.setString(index++, pattern);
            }
            statement.setInt(index, Math.max(1, Math.min(limit, 50)));
        }, this::mapArticle);
    }

    public List<KnowledgeArticle> findAllArticles() {
        return query("SELECT * FROM knowledge_articles ORDER BY updated_at DESC", null, this::mapArticle);
    }

    public long insertArticle(KnowledgeArticle article, Long actorId) {
        return executeTransactionCallback(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO knowledge_articles (title, summary, content, category_id, keywords, status, "
                        + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, article.getTitle());
                statement.setString(2, article.getSummary());
                statement.setString(3, article.getContent());
                if (article.getCategoryId() == null) statement.setNull(4, Types.BIGINT);
                else statement.setLong(4, article.getCategoryId());
                statement.setString(5, article.getKeywords());
                statement.setString(6, article.getStatus());
                statement.setLong(7, actorId);
                statement.setLong(8, actorId);
                statement.executeUpdate();
                try (var keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            }
            throw new IllegalStateException("Failed to create knowledge article");
        });
    }

    public List<ReplyTemplate> findEnabledTemplates(Long categoryId) {
        return query("SELECT * FROM reply_templates WHERE enabled = 1 AND (category_id IS NULL OR category_id = ?) "
                + "ORDER BY (category_id IS NULL), template_name",
            statement -> {
                if (categoryId == null) statement.setNull(1, Types.BIGINT); else statement.setLong(1, categoryId);
            }, this::mapTemplate);
    }

    public List<ReplyTemplate> findAllTemplates() {
        return query("SELECT * FROM reply_templates ORDER BY enabled DESC, template_name", null, this::mapTemplate);
    }

    public void insertTemplate(ReplyTemplate template, Long actorId) {
        update("INSERT INTO reply_templates (template_name, content, category_id, enabled, created_by) "
                + "VALUES (?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, template.getTemplateName());
            statement.setString(2, template.getContent());
            if (template.getCategoryId() == null) statement.setNull(3, Types.BIGINT);
            else statement.setLong(3, template.getCategoryId());
            statement.setInt(4, template.isEnabled() ? 1 : 0);
            statement.setLong(5, actorId);
        });
    }

    public List<HandlingMacro> findEnabledMacros() {
        return query("SELECT m.*, t.template_id AS t_id, t.template_name, t.content, t.category_id, "
                + "t.enabled AS template_enabled FROM handling_macros m "
                + "LEFT JOIN reply_templates t ON t.template_id = m.reply_template_id "
                + "WHERE m.enabled = 1 ORDER BY m.macro_name",
            null, this::mapMacro);
    }

    public void insertMacro(HandlingMacro macro, Long actorId) {
        update("INSERT INTO handling_macros (macro_name, reply_template_id, target_status, enabled, created_by) "
                + "VALUES (?, ?, ?, ?, ?)", statement -> {
            statement.setString(1, macro.getMacroName());
            if (macro.getReplyTemplate() == null || macro.getReplyTemplate().getTemplateId() == null) {
                statement.setNull(2, Types.BIGINT);
            } else statement.setLong(2, macro.getReplyTemplate().getTemplateId());
            if (macro.getTargetStatus() == null) statement.setNull(3, Types.TINYINT);
            else statement.setInt(3, macro.getTargetStatus());
            statement.setInt(4, macro.isEnabled() ? 1 : 0);
            statement.setLong(5, actorId);
        });
    }

    private KnowledgeArticle mapArticle(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        KnowledgeArticle value = new KnowledgeArticle();
        value.setArticleId(resultSet.getLong("article_id"));
        value.setTitle(resultSet.getString("title"));
        value.setSummary(resultSet.getString("summary"));
        value.setContent(resultSet.getString("content"));
        value.setCategoryId(nullableLong(resultSet, "category_id"));
        value.setKeywords(resultSet.getString("keywords"));
        value.setStatus(resultSet.getString("status"));
        value.setCreatedBy(resultSet.getLong("created_by"));
        Timestamp updated = resultSet.getTimestamp("updated_at");
        value.setUpdatedAt(updated == null ? null : updated.toLocalDateTime());
        return value;
    }

    private ReplyTemplate mapTemplate(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        ReplyTemplate value = new ReplyTemplate();
        value.setTemplateId(resultSet.getLong("template_id"));
        value.setTemplateName(resultSet.getString("template_name"));
        value.setContent(resultSet.getString("content"));
        value.setCategoryId(nullableLong(resultSet, "category_id"));
        value.setEnabled(resultSet.getInt("enabled") == 1);
        return value;
    }

    private HandlingMacro mapMacro(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        HandlingMacro value = new HandlingMacro();
        value.setMacroId(resultSet.getLong("macro_id"));
        value.setMacroName(resultSet.getString("macro_name"));
        value.setTargetStatus(nullableInt(resultSet, "target_status"));
        value.setEnabled(resultSet.getInt("enabled") == 1);
        Long templateId = nullableLong(resultSet, "t_id");
        if (templateId != null) {
            ReplyTemplate template = new ReplyTemplate();
            template.setTemplateId(templateId);
            template.setTemplateName(resultSet.getString("template_name"));
            template.setContent(resultSet.getString("content"));
            template.setCategoryId(nullableLong(resultSet, "category_id"));
            template.setEnabled(resultSet.getInt("template_enabled") == 1);
            value.setReplyTemplate(template);
        }
        return value;
    }

    private Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInt(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
