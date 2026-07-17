package com.ticket.service;

import com.ticket.model.ServiceMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportExportServiceTest {
    @TempDir Path directory;

    @Test
    void shouldCreateCsvXlsxAndPdfArtifacts() throws Exception {
        ServiceMetrics metrics = new ServiceMetrics(12, 15.2, 83.5, 96.4, 5.1, 88.8, 4.7, 3, 1);
        List<Document> loads = List.of(new Document("username", "admin01").append("total_assigned", 12L)
            .append("open_count", 3L).append("breached_count", 1L).append("avg_backlog_hours", 5.1));
        List<Document> trend = List.of(new Document("day", "2026-07-16")
            .append("average_rating", 4.7).append("rating_count", 6L));
        ReportExportService service = new ReportExportService();
        Path csv = service.export(directory.resolve("report.csv"), metrics, loads, trend);
        Path xlsx = service.export(directory.resolve("report.xlsx"), metrics, loads, trend);
        Path pdf = service.export(directory.resolve("report.pdf"), metrics, loads, trend);

        Assertions.assertTrue(Files.size(csv) > 100);
        Assertions.assertArrayEquals(new byte[]{'P', 'K'}, first(xlsx, 2));
        Assertions.assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'}, first(pdf, 4));
    }

    private byte[] first(Path path, int count) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return java.util.Arrays.copyOf(bytes, count);
    }
}
