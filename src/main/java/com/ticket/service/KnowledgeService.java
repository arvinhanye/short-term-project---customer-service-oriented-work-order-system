package com.ticket.service;

import com.ticket.dao.mysql.KnowledgeDAO;
import com.ticket.dao.mysql.OrderDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.HandlingMacro;
import com.ticket.model.KnowledgeArticle;
import com.ticket.model.Order;
import com.ticket.model.ReplyTemplate;
import com.ticket.model.User;
import java.util.List;

public class KnowledgeService {
    private final KnowledgeDAO knowledgeDAO = new KnowledgeDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final BusinessService businessService = new BusinessService();

    public List<KnowledgeArticle> recommend(User actor, Long categoryId, String text, int limit) {
        UserService.requireActiveUser(actor);
        return knowledgeDAO.searchPublished(categoryId, text, limit);
    }

    public List<KnowledgeArticle> listArticles(User actor) {
        UserService.requireTicketStaff(actor);
        return knowledgeDAO.findAllArticles();
    }

    public long createArticle(User actor, KnowledgeArticle article) {
        UserService.requireTicketStaff(actor);
        if (article == null || blank(article.getTitle()) || blank(article.getContent())) {
            throw new BusinessException("知识文章标题和正文不能为空");
        }
        if (article.getTitle().trim().length() > 200) throw new BusinessException("文章标题不能超过 200 个字符");
        article.setTitle(article.getTitle().trim());
        article.setSummary(trim(article.getSummary(), 500));
        article.setContent(article.getContent().trim());
        article.setKeywords(trim(article.getKeywords(), 500));
        String status = article.getStatus() == null ? "DRAFT" : article.getStatus().trim().toUpperCase();
        if (!List.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new BusinessException("知识文章状态不合法");
        }
        article.setStatus(status);
        return knowledgeDAO.insertArticle(article, actor.getUserId());
    }

    public List<ReplyTemplate> templates(User actor, Long categoryId) {
        UserService.requireTicketStaff(actor);
        return knowledgeDAO.findEnabledTemplates(categoryId);
    }

    public List<ReplyTemplate> allTemplates(User actor) {
        UserService.requireTicketStaff(actor);
        return knowledgeDAO.findAllTemplates();
    }

    public void createTemplate(User actor, ReplyTemplate template) {
        UserService.requireTicketStaff(actor);
        if (template == null || blank(template.getTemplateName()) || blank(template.getContent())) {
            throw new BusinessException("模板名称和内容不能为空");
        }
        if (template.getTemplateName().trim().length() > 100) throw new BusinessException("模板名称不能超过 100 个字符");
        template.setTemplateName(template.getTemplateName().trim());
        template.setContent(template.getContent().trim());
        knowledgeDAO.insertTemplate(template, actor.getUserId());
    }

    public List<HandlingMacro> macros(User actor) {
        UserService.requireTicketStaff(actor);
        return knowledgeDAO.findEnabledMacros();
    }

    public void createMacro(User actor, HandlingMacro macro) {
        UserService.requireTicketStaff(actor);
        if (macro == null || blank(macro.getMacroName())) throw new BusinessException("处理宏名称不能为空");
        if (macro.getReplyTemplate() == null && macro.getTargetStatus() == null) {
            throw new BusinessException("处理宏至少需要一个回复模板或目标状态");
        }
        if (macro.getTargetStatus() != null && !List.of(1, 2, 4, 5, 6).contains(macro.getTargetStatus())) {
            throw new BusinessException("处理宏目标状态不合法");
        }
        macro.setMacroName(macro.getMacroName().trim());
        knowledgeDAO.insertMacro(macro, actor.getUserId());
    }

    public void applyMacro(User actor, Long itemId, Long macroId) {
        UserService.requireTicketStaff(actor);
        HandlingMacro macro = knowledgeDAO.findEnabledMacros().stream()
            .filter(value -> value.getMacroId().equals(macroId)).findFirst()
            .orElseThrow(() -> new BusinessException("处理宏不存在或已停用"));
        if (macro.getReplyTemplate() != null && !blank(macro.getReplyTemplate().getContent())) {
            businessService.addAdminReply(actor, itemId, macro.getReplyTemplate().getContent());
        }
        if (macro.getTargetStatus() != null) {
            Order order = orderDAO.findByItemId(itemId);
            if (order == null) throw new BusinessException("工单不存在");
            businessService.changeOrderStatus(actor, order.getOrderId(), macro.getTargetStatus(),
                "处理宏：" + macro.getMacroName());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) throw new BusinessException("内容不能超过 " + maxLength + " 个字符");
        return normalized;
    }
}
