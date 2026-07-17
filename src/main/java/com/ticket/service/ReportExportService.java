package com.ticket.service;

import com.ticket.exception.BusinessException;
import com.ticket.model.ServiceMetrics;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;

public class ReportExportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Path export(Path target, ServiceMetrics metrics, List<Document> administratorLoad,
                       List<Document> satisfactionTrend) {
        if (target == null || metrics == null) throw new BusinessException("导出参数不完整");
        String name = target.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            if (name.endsWith(".csv")) writeCsv(target, metrics, administratorLoad, satisfactionTrend);
            else if (name.endsWith(".xlsx")) writeXlsx(target, metrics, administratorLoad, satisfactionTrend);
            else if (name.endsWith(".pdf")) writePdf(target, metrics, administratorLoad, satisfactionTrend);
            else throw new BusinessException("仅支持 CSV、XLSX 或 PDF 格式");
            return target;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("导出报表失败", ex);
        }
    }

    private void writeCsv(Path target, ServiceMetrics metrics, List<Document> loads, List<Document> trend)
            throws IOException {
        try (OutputStream output = Files.newOutputStream(target)) {
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(output,
                    StandardCharsets.UTF_8))) {
                writer.write("服务质量报表,生成时间," + LocalDateTime.now().format(TIME));
                writer.newLine();
                writer.write("指标,值");
                writer.newLine();
                for (String[] row : metricRows(metrics)) {
                    writer.write(csv(row[0]) + "," + csv(row[1]));
                    writer.newLine();
                }
                writer.newLine();
                writer.write("管理员,累计分配,当前积压,SLA超时,平均积压小时");
                writer.newLine();
                for (Document load : safe(loads)) {
                    writer.write(csv(load.getString("username")) + "," + value(load, "total_assigned") + ","
                        + value(load, "open_count") + "," + value(load, "breached_count") + ","
                        + value(load, "avg_backlog_hours"));
                    writer.newLine();
                }
                writer.newLine();
                writer.write("日期,平均满意度,评价数");
                writer.newLine();
                for (Document item : safe(trend)) {
                    writer.write(csv(item.getString("day")) + "," + value(item, "average_rating") + ","
                        + value(item, "rating_count"));
                    writer.newLine();
                }
            }
        }
    }

    private void writeXlsx(Path target, ServiceMetrics metrics, List<Document> loads, List<Document> trend)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            var metricsSheet = workbook.createSheet("服务指标");
            writeRow(metricsSheet.createRow(0), header, "指标", "值");
            int rowIndex = 1;
            for (String[] row : metricRows(metrics)) writeRow(metricsSheet.createRow(rowIndex++), null, row);
            metricsSheet.setColumnWidth(0, 30 * 256);
            metricsSheet.setColumnWidth(1, 18 * 256);

            var loadSheet = workbook.createSheet("管理员负载");
            writeRow(loadSheet.createRow(0), header, "管理员", "累计分配", "当前积压", "SLA超时", "平均积压小时");
            rowIndex = 1;
            for (Document load : safe(loads)) writeRow(loadSheet.createRow(rowIndex++), null,
                load.getString("username"), value(load, "total_assigned"), value(load, "open_count"),
                value(load, "breached_count"), value(load, "avg_backlog_hours"));
            for (int i = 0; i < 5; i++) loadSheet.setColumnWidth(i, (i == 0 ? 22 : 16) * 256);

            var trendSheet = workbook.createSheet("满意度趋势");
            writeRow(trendSheet.createRow(0), header, "日期", "平均满意度", "评价数");
            rowIndex = 1;
            for (Document item : safe(trend)) writeRow(trendSheet.createRow(rowIndex++), null,
                item.getString("day"), value(item, "average_rating"), value(item, "rating_count"));
            for (int i = 0; i < 3; i++) trendSheet.setColumnWidth(i, 18 * 256);
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    private void writePdf(Path target, ServiceMetrics metrics, List<Document> loads, List<Document> trend)
            throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadPdfFont(document);
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            lines.add("服务质量报表  " + LocalDateTime.now().format(TIME));
            lines.add("");
            for (String[] row : metricRows(metrics)) lines.add(row[0] + "：" + row[1]);
            lines.add("");
            lines.add("管理员负载");
            for (Document load : safe(loads)) lines.add(load.getString("username") + "  积压 "
                + value(load, "open_count") + "  SLA超时 " + value(load, "breached_count")
                + "  平均积压 " + value(load, "avg_backlog_hours") + " 小时");
            lines.add("");
            lines.add("满意度趋势");
            for (Document item : safe(trend)) lines.add(item.getString("day") + "  "
                + value(item, "average_rating") + " 分 / " + value(item, "rating_count") + " 条");
            writePdfLines(document, font, lines);
            document.save(target.toFile());
        }
    }

    private void writePdfLines(PDDocument document, PDFont font, List<String> lines) throws IOException {
        PDPage page = null;
        PDPageContentStream stream = null;
        float y = 0;
        try {
            for (String line : lines) {
                if (stream == null || y < 50) {
                    if (stream != null) {
                        stream.endText();
                        stream.close();
                    }
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.setLeading(16);
                    stream.newLineAtOffset(48, page.getMediaBox().getHeight() - 48);
                    y = page.getMediaBox().getHeight() - 48;
                }
                stream.showText(sanitizePdf(line, font));
                stream.newLine();
                y -= 16;
            }
            if (stream != null) stream.endText();
        } finally {
            if (stream != null) stream.close();
        }
    }

    private PDFont loadPdfFont(PDDocument document) throws IOException {
        List<Path> candidates = List.of(
            Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
            Path.of("/Library/Fonts/Arial Unicode.ttf"),
            Path.of("C:/Windows/Fonts/msyh.ttf"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (var input = Files.newInputStream(candidate)) {
                    return PDType0Font.load(document, input);
                }
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private String sanitizePdf(String value, PDFont font) {
        try {
            font.encode(value);
            return value;
        } catch (Exception ignored) {
            return value.replaceAll("[^\\x20-\\x7E]", "?");
        }
    }

    private void writeRow(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; i++) {
            var cell = row.createCell(i);
            cell.setCellValue(values[i] == null ? "" : values[i]);
            if (style != null) cell.setCellStyle(style);
        }
    }

    private List<String[]> metricRows(ServiceMetrics metrics) {
        return List.of(
            row("工单总量", metrics.ticketCount()),
            row("平均首次响应时间（分钟）", metrics.averageFirstResponseMinutes()),
            row("平均解决时间（分钟）", metrics.averageResolutionMinutes()),
            row("SLA 达标率（%）", metrics.slaComplianceRate()),
            row("平均积压时长（小时）", metrics.averageBacklogHours()),
            row("一次解决率（%）", metrics.firstContactResolutionRate()),
            row("平均满意度", metrics.averageSatisfaction()),
            row("当前积压量", metrics.openBacklog()),
            row("SLA 超时量", metrics.breachedCount()));
    }

    private String[] row(String label, Object value) {
        return new String[]{label, value instanceof Double number ? String.format(java.util.Locale.ROOT, "%.2f", number)
            : String.valueOf(value)};
    }

    private List<Document> safe(List<Document> values) {
        return values == null ? List.of() : values;
    }

    private String value(Document document, String key) {
        Object value = document == null ? null : document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
