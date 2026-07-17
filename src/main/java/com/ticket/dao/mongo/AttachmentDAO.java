package com.ticket.dao.mongo;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.ticket.dao.MongoBaseDAO;
import com.ticket.model.TicketAttachment;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.Set;

public class AttachmentDAO extends MongoBaseDAO {
    private static final String BUCKET_NAME = "ticket_files";

    public TicketAttachment upload(Path source, String itemId, String userId) throws Exception {
        String fileName = normalizedFileName(source);
        String contentType = detectContentType(source);
        long size = Files.size(source);
        Document metadata = new Document("item_id", itemId)
            .append("uploaded_by", userId)
            .append("content_type", contentType)
            .append("size", size)
            .append("image", contentType.startsWith("image/"));
        ObjectId fileId;
        try (InputStream input = Files.newInputStream(source)) {
            fileId = bucket().uploadFromStream(fileName, input,
                new GridFSUploadOptions().metadata(metadata));
        }
        TicketAttachment attachment = new TicketAttachment();
        attachment.setFileId(fileId.toHexString());
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSize(size);
        attachment.setImage(contentType.startsWith("image/"));
        return attachment;
    }

    public void download(String fileId, Path destination) throws Exception {
        try (OutputStream output = Files.newOutputStream(destination,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            bucket().downloadToStream(new ObjectId(fileId), output);
        }
    }

    public void delete(String fileId) {
        if (fileId == null || !ObjectId.isValid(fileId)) {
            return;
        }
        bucket().delete(new ObjectId(fileId));
    }

    public long deleteOrphans(Set<String> referencedFileIds) {
        Set<String> referenced = referencedFileIds == null ? Set.of() : referencedFileIds;
        long deleted = 0;
        for (var file : bucket().find()) {
            String id = file.getObjectId().toHexString();
            if (!referenced.contains(id)) {
                bucket().delete(file.getObjectId());
                deleted++;
            }
        }
        return deleted;
    }

    private GridFSBucket bucket() {
        return GridFSBuckets.create(database(), BUCKET_NAME);
    }

    private String normalizedFileName(Path source) {
        String name = source.getFileName() == null ? "attachment" : source.getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}]", "_").trim();
        if (name.isBlank()) {
            name = "attachment";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private String detectContentType(Path source) {
        try {
            String detected = Files.probeContentType(source);
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (Exception ignored) {
            // Fall through to a safe generic type.
        }
        String name = source.getFileName() == null ? "" : source.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "application/octet-stream";
    }
}
