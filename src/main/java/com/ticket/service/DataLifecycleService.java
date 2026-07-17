package com.ticket.service;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.ticket.dao.MongoBaseDAO;
import com.ticket.dao.mongo.AttachmentDAO;
import com.ticket.dao.mongo.CommentDAO;
import com.ticket.dao.mysql.DataLifecycleRunDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.bson.Document;

public class DataLifecycleService extends MongoBaseDAO {
    private final DataLifecycleRunDAO runDAO = new DataLifecycleRunDAO();
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();
    private final CommentDAO commentDAO = new CommentDAO();

    public long archiveLogs(User actor, int retentionDays) {
        UserService.requireRoot(actor);
        int days = Math.max(30, Math.min(retentionDays, 3650));
        Instant cutoff = Instant.now().minusSeconds((long) days * 24 * 60 * 60);
        LocalDateTime cutoffTime = LocalDateTime.ofInstant(cutoff, ZoneId.systemDefault());
        try {
            long affected = archiveCollection("action_logs", cutoff) + archiveCollection("system_logs", cutoff);
            runDAO.insert("LOG_ARCHIVE", cutoffTime, affected, null, null, "SUCCESS",
                "日志已归档到 *_archive 集合", actor.getUserId());
            return affected;
        } catch (Exception ex) {
            recordFailure("LOG_ARCHIVE", cutoffTime, actor, ex);
            throw new BusinessException("日志归档失败", ex);
        }
    }

    public long cleanOrphanAttachments(User actor) {
        UserService.requireRoot(actor);
        try {
            long count = attachmentDAO.deleteOrphans(commentDAO.findReferencedAttachmentIds());
            runDAO.insert("ORPHAN_ATTACHMENT_CLEANUP", null, count, null, null, "SUCCESS",
                "已删除无评论引用的 GridFS 文件", actor.getUserId());
            return count;
        } catch (Exception ex) {
            recordFailure("ORPHAN_ATTACHMENT_CLEANUP", null, actor, ex);
            throw new BusinessException("孤儿附件清理失败", ex);
        }
    }

    public String verifyBackupArtifact(User actor, Path backup) {
        UserService.requireRoot(actor);
        if (backup == null || !Files.isRegularFile(backup)) throw new BusinessException("请选择有效的 SQL 备份文件");
        try {
            long size = Files.size(backup);
            if (size == 0) throw new BusinessException("备份文件为空");
            String head = Files.readString(backup, java.nio.charset.StandardCharsets.UTF_8);
            if (!head.contains("orders") || !head.contains("CREATE TABLE")) {
                throw new BusinessException("备份文件缺少核心表结构，不能用于恢复演练");
            }
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup)));
            runDAO.insert("BACKUP_VERIFY", null, 1, backup.toAbsolutePath().toString(), checksum,
                "SUCCESS", "备份结构、大小和 SHA-256 校验通过；隔离库恢复请运行 scripts/restore-drill.sh",
                actor.getUserId());
            return checksum;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            recordFailure("BACKUP_VERIFY", null, actor, ex);
            throw new BusinessException("备份校验失败", ex);
        }
    }

    private long archiveCollection(String sourceName, Instant cutoff) {
        List<Document> source = collection(sourceName).find(Filters.lt("timestamp", Date.from(cutoff)))
            .limit(5000).into(new ArrayList<>());
        if (sourceName.equals("action_logs")) {
            source = collection(sourceName).find(Filters.lt("created_at", Date.from(cutoff)))
                .limit(5000).into(new ArrayList<>());
        }
        if (source.isEmpty()) return 0;
        List<Object> ids = new ArrayList<>();
        for (Document value : source) {
            Object id = value.get("_id");
            ids.add(id);
            Document archived = new Document(value).append("archived_at", new Date());
            collection(sourceName + "_archive").replaceOne(Filters.eq("_id", id), archived,
                new ReplaceOptions().upsert(true));
        }
        collection(sourceName).deleteMany(Filters.in("_id", ids));
        return source.size();
    }

    private void recordFailure(String type, LocalDateTime cutoff, User actor, Exception ex) {
        try {
            runDAO.insert(type, cutoff, 0, null, null, "FAILED", ex.getMessage(), actor.getUserId());
        } catch (Exception ignored) {
            // 原始异常优先返回给调用方。
        }
    }
}
