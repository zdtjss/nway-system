package com.nway.system.ai.export;

import com.nway.system.ai.web.model.AiExportRequest;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图表渲染：把结构化 chart 数据绘制为 PNG（嵌入 Word / PPT / Excel）或 SVG（HTML）。
 *
 * @author nway
 */
public final class AiExportCharts {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 540;
    private static final int PLOT_LEFT = 84;
    private static final int PLOT_RIGHT = 930;
    private static final int PLOT_TOP = 40;
    private static final int PLOT_BOTTOM = 456;
    private static final int LEGEND_Y = 500;
    private static final int MAX_SLICES = 12;

    private static final Color[] COLORS = {
            new Color(0x16, 0x64, 0xFF),
            new Color(0x00, 0xB4, 0x2A),
            new Color(0xFF, 0x7D, 0x00),
            new Color(0xF5, 0x3F, 0x3F),
            new Color(0x72, 0x2E, 0xD1),
            new Color(0x00, 0xA6, 0xB8),
            new Color(0xD4, 0x88, 0x00),
            new Color(0x8F, 0x95, 0x9E)
    };

    private static final String FONT_FAMILY = resolveFontFamily();

    private AiExportCharts() {
    }

    /** 生成 PNG 图表；无有效数据时返回带提示的空白图。 */
    public static byte[] renderPng(AiExportRequest.Chart chart) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);
            List<String> categories = categories(chart);
            List<AiExportRequest.Series> series = series(chart, categories.size());
            if (series.isEmpty()) {
                graphics.setColor(new Color(0xA9, 0xAE, 0xB8));
                graphics.setFont(font(Font.PLAIN, 16));
                drawCenter(graphics, "暂无图表数据", WIDTH / 2, HEIGHT / 2);
                return toBytes(image);
            }
            if ("pie".equalsIgnoreCase(chart.getKind())) {
                drawPie(graphics, categories, series.get(0));
            } else if ("line".equalsIgnoreCase(chart.getKind())) {
                drawLine(graphics, categories, series);
            } else {
                drawBar(graphics, categories, series);
            }
            drawLegend(graphics, chart, categories, series);
            return toBytes(image);
        } finally {
            graphics.dispose();
        }
    }

    /** 生成内联 SVG 图表，供 HTML 导出直接展示（无外部依赖）。 */
    public static String renderSvg(AiExportRequest.Chart chart) {
        List<String> categories = categories(chart);
        List<AiExportRequest.Series> series = series(chart, categories.size());
        StringBuilder svg = new StringBuilder();
        svg.append("<svg class=\"chart\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" role=\"img\" aria-label=\"数据图表\" xmlns=\"http://www.w3.org/2000/svg\">");
        svg.append("<rect width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT)
                .append("\" fill=\"#fff\"/>");
        if (series.isEmpty()) {
            svg.append("<text x=\"").append(WIDTH / 2).append("\" y=\"").append(HEIGHT / 2)
                    .append("\" text-anchor=\"middle\" fill=\"#a9aeb8\" font-size=\"16\">暂无图表数据</text></svg>");
            return svg.toString();
        }
        double max = maxValue(series);
        int ticks = 4;
        double step = max / (ticks - 1 > 0 ? ticks - 1 : 1);
        if ("pie".equalsIgnoreCase(chart.getKind())) {
            appendPieSvg(svg, categories, series.get(0));
        } else {
            for (int i = 0; i < ticks; i++) {
                double value = step * i;
                double y = PLOT_BOTTOM - (value / max) * (PLOT_BOTTOM - PLOT_TOP);
                svg.append("<line x1=\"").append(PLOT_LEFT).append("\" y1=\"").append(round(y))
                        .append("\" x2=\"").append(PLOT_RIGHT).append("\" y2=\"").append(round(y))
                        .append("\" stroke=\"#eef0f3\"/>");
                svg.append("<text x=\"").append(PLOT_LEFT - 8).append("\" y=\"").append(round(y + 4))
                        .append("\" text-anchor=\"end\" fill=\"#a9aeb8\" font-size=\"11\">")
                        .append(escape(format(value))).append("</text>");
            }
            svg.append("<line x1=\"").append(PLOT_LEFT).append("\" y1=\"").append(PLOT_BOTTOM)
                    .append("\" x2=\"").append(PLOT_RIGHT).append("\" y2=\"").append(PLOT_BOTTOM)
                    .append("\" stroke=\"#d9dde5\"/>");
            boolean line = "line".equalsIgnoreCase(chart.getKind());
            double plotWidth = PLOT_RIGHT - PLOT_LEFT;
            double groupWidth = categories.isEmpty() ? plotWidth : plotWidth / categories.size();
            if (line) {
                for (int s = 0; s < series.size(); s++) {
                    AiExportRequest.Series item = series.get(s);
                    String color = color(s);
                    StringBuilder points = new StringBuilder();
                    for (int i = 0; i < categories.size(); i++) {
                        double value = valueAt(item, i);
                        double x = categories.size() <= 1 ? PLOT_LEFT + plotWidth / 2
                                : PLOT_LEFT + (plotWidth * i / (categories.size() - 1));
                        double y = PLOT_BOTTOM - (value / max) * (PLOT_BOTTOM - PLOT_TOP);
                        points.append(round(x)).append(',').append(round(y)).append(' ');
                        svg.append("<circle cx=\"").append(round(x)).append("\" cy=\"").append(round(y))
                                .append("\" r=\"3\" fill=\"").append(color).append("\"/>");
                    }
                    svg.append("<polyline fill=\"none\" stroke=\"").append(color)
                            .append("\" stroke-width=\"2\" points=\"").append(points.toString().trim())
                            .append("\"/>");
                }
            } else {
                double barWidth = Math.max(2, groupWidth * 0.72 / series.size());
                for (int i = 0; i < categories.size(); i++) {
                    double offset = PLOT_LEFT + i * groupWidth + groupWidth * 0.14;
                    for (int s = 0; s < series.size(); s++) {
                        double value = valueAt(series.get(s), i);
                        double height = Math.max(0, (value / max) * (PLOT_BOTTOM - PLOT_TOP));
                        double x = offset + s * barWidth;
                        svg.append("<rect x=\"").append(round(x)).append("\" y=\"").append(round(PLOT_BOTTOM - height))
                                .append("\" width=\"").append(round(Math.max(1, barWidth - 1)))
                                .append("\" height=\"").append(round(height))
                                .append("\" fill=\"").append(color(s)).append("\" rx=\"2\"/>");
                    }
                }
            }
            appendXLabelsSvg(svg, categories, groupWidth, line, plotWidth);
        }
        appendLegendSvg(svg, chart, categories, series);
        svg.append("</svg>");
        return svg.toString();
    }

    private static void drawBar(Graphics2D graphics, List<String> categories, List<AiExportRequest.Series> series) {
        double max = maxValue(series);
        drawGrid(graphics, max);
        double plotWidth = PLOT_RIGHT - PLOT_LEFT;
        double groupWidth = categories.isEmpty() ? plotWidth : plotWidth / categories.size();
        double barWidth = Math.max(2, groupWidth * 0.72 / series.size());
        for (int i = 0; i < categories.size(); i++) {
            double offset = PLOT_LEFT + i * groupWidth + groupWidth * 0.14;
            for (int s = 0; s < series.size(); s++) {
                double value = valueAt(series.get(s), i);
                double height = Math.max(0, (value / max) * (PLOT_BOTTOM - PLOT_TOP));
                graphics.setColor(colorValue(s));
                graphics.fill(new Rectangle2D.Double(offset + s * barWidth, PLOT_BOTTOM - height,
                        Math.max(1, barWidth - 1), height));
            }
        }
        drawAxisLine(graphics);
        drawCategoryLabels(graphics, categories, groupWidth);
    }

    private static void drawLine(Graphics2D graphics, List<String> categories, List<AiExportRequest.Series> series) {
        double max = maxValue(series);
        drawGrid(graphics, max);
        double plotWidth = PLOT_RIGHT - PLOT_LEFT;
        for (int s = 0; s < series.size(); s++) {
            AiExportRequest.Series item = series.get(s);
            graphics.setColor(colorValue(s));
            graphics.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < categories.size(); i++) {
                double value = valueAt(item, i);
                double x = xPoint(i, categories.size(), plotWidth);
                double y = PLOT_BOTTOM - (value / max) * (PLOT_BOTTOM - PLOT_TOP);
                if (i > 0) {
                    double prevValue = valueAt(item, i - 1);
                    double prevX = xPoint(i - 1, categories.size(), plotWidth);
                    double prevY = PLOT_BOTTOM - (prevValue / max) * (PLOT_BOTTOM - PLOT_TOP);
                    graphics.draw(new Line2D.Double(prevX, prevY, x, y));
                }
                graphics.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
            }
        }
        graphics.setStroke(new BasicStroke(1f));
        drawAxisLine(graphics);
        double groupWidth = categories.isEmpty() ? plotWidth : plotWidth / categories.size();
        drawCategoryLabels(graphics, categories, groupWidth);
    }

    private static void drawPie(Graphics2D graphics, List<String> categories, AiExportRequest.Series series) {
        List<Slice> slices = slices(categories, series);
        int fontSize = slices.size() > 8 ? 11 : 12;
        int legendTop = 90;
        int legendStep = 26;
        int legendX = 560;
        double total = slices.stream().mapToDouble(Slice::value).sum();
        int index = 0;
        for (Slice slice : slices) {
            double extent = total <= 0 ? 0 : -(slice.value() / total) * 360;
            graphics.setColor(colorValue(index));
            graphics.fill(new Arc2D.Double(110, 90, 360, 360, 90, extent, Arc2D.PIE));
            graphics.setColor(Color.WHITE);
            graphics.setStroke(new BasicStroke(1.2f));
            graphics.draw(new Arc2D.Double(110, 90, 360, 360, 90, extent, Arc2D.PIE));
            graphics.setStroke(new BasicStroke(1f));

            graphics.setColor(colorValue(index));
            graphics.fill(new Rectangle2D.Double(legendX, legendTop + index * legendStep, 12, 12));
            graphics.setColor(new Color(0x4E, 0x59, 0x69));
            graphics.setFont(font(Font.PLAIN, fontSize));
            int percent = total <= 0 ? 0 : (int) Math.round(slice.value() / total * 100);
            String label = slice.label() + "  " + format(slice.value()) + "（" + percent + "%）";
            graphics.drawString(clip(graphics, label, 640 - legendX - 30), legendX + 20,
                    legendTop + index * legendStep + 11);
            index++;
        }
    }

    private static void drawGrid(Graphics2D graphics, double max) {
        graphics.setFont(font(Font.PLAIN, 11));
        int ticks = 4;
        double step = max / (ticks - 1 > 0 ? ticks - 1 : 1);
        for (int i = 0; i < ticks; i++) {
            double value = step * i;
            double y = PLOT_BOTTOM - (value / max) * (PLOT_BOTTOM - PLOT_TOP);
            graphics.setColor(new Color(0xEE, 0xF0, 0xF3));
            graphics.draw(new Line2D.Double(PLOT_LEFT, y, PLOT_RIGHT, y));
            graphics.setColor(new Color(0xA9, 0xAE, 0xB8));
            String label = format(value);
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(label, PLOT_LEFT - 8 - metrics.stringWidth(label), (float) (y + 4));
        }
    }

    private static void drawAxisLine(Graphics2D graphics) {
        graphics.setColor(new Color(0xD9, 0xDD, 0xE5));
        graphics.draw(new Line2D.Double(PLOT_LEFT, PLOT_BOTTOM, PLOT_RIGHT, PLOT_BOTTOM));
    }

    private static void drawCategoryLabels(Graphics2D graphics, List<String> categories, double groupWidth) {
        graphics.setColor(new Color(0xA9, 0xAE, 0xB8));
        graphics.setFont(font(Font.PLAIN, 10));
        int step = Math.max(1, (int) Math.ceil(categories.size() / 12.0));
        FontMetrics metrics = graphics.getFontMetrics();
        for (int i = 0; i < categories.size(); i += step) {
            String label = clip(graphics, categories.get(i), Math.max(36, (int) groupWidth * step - 6));
            double x = categories.size() <= 1
                    ? (PLOT_LEFT + PLOT_RIGHT) / 2.0
                    : PLOT_LEFT + groupWidth * i + groupWidth / 2;
            graphics.drawString(label, (float) (x - metrics.stringWidth(label) / 2.0), PLOT_BOTTOM + 18);
        }
    }

    private static void drawLegend(Graphics2D graphics, AiExportRequest.Chart chart, List<String> categories,
                                   List<AiExportRequest.Series> series) {
        graphics.setFont(font(Font.PLAIN, 11));
        int x = PLOT_LEFT;
        List<String> labels = new ArrayList<>();
        if ("pie".equalsIgnoreCase(chart.getKind())) {
            // 饼图图例已随扇区绘制
            return;
        }
        for (AiExportRequest.Series item : series) {
            labels.add(item.getName() == null || item.getName().isBlank() ? "数值" : item.getName());
        }
        for (int i = 0; i < labels.size(); i++) {
            graphics.setColor(colorValue(i));
            graphics.fill(new Rectangle2D.Double(x, LEGEND_Y - 9, 10, 10));
            graphics.setColor(new Color(0x6B, 0x72, 0x80));
            String label = labels.get(i);
            graphics.drawString(label, x + 15, LEGEND_Y);
            x += 15 + graphics.getFontMetrics().stringWidth(label) + 24;
        }
    }

    private static void appendXLabelsSvg(StringBuilder svg, List<String> categories, double groupWidth,
                                         boolean line, double plotWidth) {
        int step = Math.max(1, (int) Math.ceil(categories.size() / 12.0));
        for (int i = 0; i < categories.size(); i += step) {
            double x = line
                    ? (categories.size() <= 1 ? PLOT_LEFT + plotWidth / 2
                    : PLOT_LEFT + plotWidth * i / (categories.size() - 1))
                    : (categories.size() <= 1 ? (PLOT_LEFT + PLOT_RIGHT) / 2.0
                    : PLOT_LEFT + groupWidth * i + groupWidth / 2);
            String label = categories.get(i);
            if (label.length() > 8) {
                label = label.substring(0, 8) + "…";
            }
            svg.append("<text x=\"").append(round(x)).append("\" y=\"").append(PLOT_BOTTOM + 18)
                    .append("\" text-anchor=\"middle\" fill=\"#a9aeb8\" font-size=\"10\">")
                    .append(escape(label)).append("</text>");
        }
    }

    private static void appendPieSvg(StringBuilder svg, List<String> categories, AiExportRequest.Series series) {
        List<Slice> slices = slices(categories, series);
        double total = slices.stream().mapToDouble(Slice::value).sum();
        double centerX = 290;
        double centerY = 270;
        double radius = 180;
        double angle = 90;
        for (int i = 0; i < slices.size(); i++) {
            Slice slice = slices.get(i);
            double extent = total <= 0 ? 0 : -(slice.value() / total) * 360;
            if (slices.size() == 1) {
                svg.append("<circle cx=\"").append(centerX).append("\" cy=\"").append(centerY)
                        .append("\" r=\"").append(radius).append("\" fill=\"").append(color(i)).append("\"/>");
            } else {
                double startRad = Math.toRadians(-angle);
                double endRad = Math.toRadians(-(angle + extent));
                double x1 = centerX + radius * Math.cos(startRad);
                double y1 = centerY + radius * Math.sin(startRad);
                double x2 = centerX + radius * Math.cos(endRad);
                double y2 = centerY + radius * Math.sin(endRad);
                int largeArc = Math.abs(extent) > 180 ? 1 : 0;
                svg.append("<path d=\"M ").append(round(centerX)).append(' ').append(round(centerY))
                        .append(" L ").append(round(x1)).append(' ').append(round(y1))
                        .append(" A ").append(radius).append(' ').append(radius)
                        .append(" 0 ").append(largeArc).append(" 1 ")
                        .append(round(x2)).append(' ').append(round(y2))
                        .append(" Z\" fill=\"").append(color(i)).append("\"/>");
            }
            angle += extent;

            svg.append("<rect x=\"560\" y=\"").append(92 + i * 26).append("\" width=\"12\" height=\"12\" fill=\"")
                    .append(color(i)).append("\"/>");
            int percent = total <= 0 ? 0 : (int) Math.round(slice.value() / total * 100);
            svg.append("<text x=\"582\" y=\"").append(92 + i * 26 + 11)
                    .append("\" fill=\"#4e5969\" font-size=\"12\">")
                    .append(escape(slice.label() + "  " + format(slice.value()) + "（" + percent + "%）"))
                    .append("</text>");
        }
    }

    private static void appendLegendSvg(StringBuilder svg, AiExportRequest.Chart chart, List<String> categories,
                                        List<AiExportRequest.Series> series) {
        if ("pie".equalsIgnoreCase(chart.getKind())) {
            return;
        }
        int x = PLOT_LEFT;
        for (int i = 0; i < series.size(); i++) {
            String label = series.get(i).getName() == null || series.get(i).getName().isBlank()
                    ? "数值" : series.get(i).getName();
            svg.append("<rect x=\"").append(x).append("\" y=\"").append(LEGEND_Y - 9)
                    .append("\" width=\"10\" height=\"10\" fill=\"").append(color(i)).append("\"/>");
            svg.append("<text x=\"").append(x + 15).append("\" y=\"").append(LEGEND_Y)
                    .append("\" fill=\"#6b7280\" font-size=\"11\">").append(escape(label)).append("</text>");
            x += 15 + Math.max(20, label.length() * 7) + 24;
        }
    }

    private static List<Slice> slices(List<String> categories, AiExportRequest.Series series) {
        List<Slice> slices = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            double value = valueAt(series, i);
            if (value > 0) {
                slices.add(new Slice(categories.get(i), value));
            }
        }
        if (slices.size() > MAX_SLICES) {
            List<Slice> limited = new ArrayList<>(slices.subList(0, MAX_SLICES - 1));
            double other = slices.subList(MAX_SLICES - 1, slices.size()).stream()
                    .mapToDouble(Slice::value).sum();
            limited.add(new Slice("其他", other));
            return limited;
        }
        if (slices.isEmpty()) {
            slices.add(new Slice("无数据", 1));
        }
        return slices;
    }

    private record Slice(String label, double value) {
    }

    private static List<String> categories(AiExportRequest.Chart chart) {
        List<String> categories = chart == null || chart.getCategories() == null
                ? new ArrayList<>() : new ArrayList<>(chart.getCategories());
        if (categories.isEmpty()) {
            int size = chart == null || chart.getSeries() == null || chart.getSeries().isEmpty()
                    ? 0 : chart.getSeries().get(0).getData() == null ? 0 : chart.getSeries().get(0).getData().size();
            for (int i = 0; i < size; i++) {
                categories.add(String.valueOf(i + 1));
            }
        }
        return categories;
    }

    private static List<AiExportRequest.Series> series(AiExportRequest.Chart chart, int categorySize) {
        List<AiExportRequest.Series> result = new ArrayList<>();
        if (chart == null || chart.getSeries() == null) {
            return result;
        }
        for (AiExportRequest.Series item : chart.getSeries()) {
            if (item == null) {
                continue;
            }
            List<Double> data = new ArrayList<>();
            for (int i = 0; i < categorySize; i++) {
                List<Double> raw = item.getData();
                data.add(raw != null && i < raw.size() && raw.get(i) != null ? raw.get(i) : 0D);
            }
            AiExportRequest.Series normalized = new AiExportRequest.Series();
            normalized.setName(item.getName());
            normalized.setData(data);
            result.add(normalized);
        }
        return result;
    }

    private static double maxValue(List<AiExportRequest.Series> series) {
        double max = 0;
        for (AiExportRequest.Series item : series) {
            for (double value : item.getData()) {
                if (Double.isFinite(value)) {
                    max = Math.max(max, value);
                }
            }
        }
        return max > 0 ? max : 1;
    }

    private static double valueAt(AiExportRequest.Series series, int index) {
        if (series.getData() == null || index >= series.getData().size()) {
            return 0;
        }
        Double value = series.getData().get(index);
        return value == null || !Double.isFinite(value) ? 0 : value;
    }

    private static double xPoint(int index, int size, double plotWidth) {
        return size <= 1 ? PLOT_LEFT + plotWidth / 2 : PLOT_LEFT + plotWidth * index / (size - 1);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value) >= 10000) {
            return String.format(Locale.ROOT, "%.1f万", value / 10000);
        }
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String clip(Graphics2D graphics, String text, int maxWidth) {
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int length = text.length();
        while (length > 1 && metrics.stringWidth(text.substring(0, length) + ellipsis) > maxWidth) {
            length--;
        }
        return text.substring(0, length) + ellipsis;
    }

    private static String color(int index) {
        Color color = COLORS[index % COLORS.length];
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color colorValue(int index) {
        return COLORS[index % COLORS.length];
    }

    private static void drawCenter(Graphics2D graphics, String text, int centerX, int centerY) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, centerY);
    }

    private static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    private static byte[] toBytes(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("图表渲染失败", e);
        }
    }

    private static String resolveFontFamily() {
        try {
            Set<String> names = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames(Locale.SIMPLIFIED_CHINESE)));
            for (String candidate : List.of("Microsoft YaHei", "微软雅黑", "PingFang SC",
                    "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "SimHei")) {
                if (names.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignore) {
            // 无图形环境时回退逻辑字体
        }
        return Font.SANS_SERIF;
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
