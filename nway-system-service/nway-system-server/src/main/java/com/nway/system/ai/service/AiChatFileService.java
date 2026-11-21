package com.nway.system.ai.service;

import com.nway.system.common.tenant.TenantUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nway.system.ai.web.model.AiFileVo;
import com.nway.system.ai.domain.AiUserContext;
import com.nway.system.file.domain.FileContent;
import com.nway.system.file.service.FileService;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话附件服务：独立上传链路（POST /ai/files），复用文件中心存储，但不复用其表单 UI。
 * <p>
 * 上传时只保存文件，不做文本解析；仅当对话需要理解文件内容（read_file）时才按需解析。
 * 解析结果缓存在进程内，不落长期表；会话结束后按文件中心策略清理。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiChatFileService {

    private static final int MAX_EXTRACT_CHARS = 20_000;
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_BATCH_FILES = 5;

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv", "log", "md", "json", "xml", "yaml", "yml");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final FileService fileService;

    private final com.github.benmanes.caffeine.cache.Cache<String, String> textCache =
            Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(30)).build();

    /** 已随消息发送的文件不可删除 */
    private final Set<String> sentFiles = ConcurrentHashMap.newKeySet();

    public AiChatFileService(FileService fileService) {
        this.fileService = fileService;
    }

    public AiFileVo upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NwaySystemException("文件不能为空");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new NwaySystemException("单个文件不能超过 20MB");
        }
        FileVo vo = fileService.upload(file, "ai_chat", "pending", null);
        return toVo(vo);
    }

    public AiFileVo retryExtract(String fileId, AiUserContext user) {
        assertOwned(fileId, user);
        FileContent content = fileService.open(fileId);
        String ext = extOf(content.fileName());
        AiFileVo vo = new AiFileVo();
        vo.setFileId(fileId);
        vo.setName(content.fileName());
        vo.setSize(content.size());
        vo.setMime(content.contentType());
        vo.setKind(kindOf(ext));
        vo.setPreviewUrl(previewUrl(fileId));
        try {
            String text = doExtract(content, ext);
            if (text != null) {
                textCache.put(fileId, text);
            }
            vo.setExtractionStatus("READY");
            vo.setSummary(summary(text));
        } catch (Exception e) {
            log.warn("附件文本提取失败 fileId={} error={}", fileId, e.getMessage());
            vo.setExtractionStatus("FAILED");
        }
        return vo;
    }

    public void delete(String fileId, AiUserContext user) {
        if (sentFiles.contains(fileId)) {
            throw new NwaySystemException("已发送的附件不能删除");
        }
        assertOwned(fileId, user);
        fileService.delete(fileId);
        textCache.invalidate(fileId);
    }

    /** 校验 fileId 归属：接口 FILE/FILE_ARRAY 字段提交前使用。 */
    public void assertOwned(String fileId, AiUserContext user) {
        requireOwned(fileId, user);
    }

    /**
     * 校验并返回附件元数据（上传租户内查询，不随管理员工作租户切换）。
     */
    public FileVo requireOwned(String fileId, AiUserContext user) {
        FileVo meta;
        // 文件按上传时租户存储，不随管理员工作租户切换
        String tenant = user == null ? null : user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            meta = fileService.getById(fileId);
        } else {
            // 工具在 reactor 线程执行，显式恢复租户上下文，避免文件查询 fail-closed
            Object[] holder = new Object[1];
            com.nway.system.common.tenant.TenantUtil.runAsTenant(tenant,
                    () -> holder[0] = fileService.getById(fileId));
            meta = (FileVo) holder[0];
        }
        if (meta == null) {
            throw new NwaySystemException("附件不存在或无权访问");
        }
        if (user != null && !user.isAdmin() && !user.isSuperAdmin()
                && !Strings.CS.equals(meta.getCreatorId(), user.getEmployeeId())) {
            throw new NwaySystemException("附件不存在或无权访问");
        }
        return meta;
    }

    /**
     * 历史消息附件展示：按 fileId 生成新的预览地址。
     * <p>
     * 预览票据有效期很短，不能随消息落库，只能在每次读取历史时重新生成；
     * 文件已删除或当前用户无权访问时返回 null，由调用方跳过。
     */
    public AiFileVo describe(String fileId, AiUserContext user) {
        String tenant = user == null ? null : user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return describeScoped(fileId, user);
        }
        Object[] holder = new Object[1];
        com.nway.system.common.tenant.TenantUtil.runAsTenant(tenant,
                () -> holder[0] = describeScoped(fileId, user));
        return (AiFileVo) holder[0];
    }

    private AiFileVo describeScoped(String fileId, AiUserContext user) {
        try {
            return toVo(requireOwned(fileId, user));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 附件清单：把 fileId、文件名与读取方式交给模型，供 read_file 与 FILE 字段使用。
     * <p>
     * 图片已随消息以 ImageBlock 提供视觉内容，必须显式告知模型无需 read_file，
     * 否则模型会对图片调用 read_file 并得到“无可提取文本”，白白多走一轮。
     */
    public String manifest(List<String> fileIds, AiUserContext user) {
        if (fileIds == null || fileIds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【对话附件】");
        int index = 1;
        for (String fileId : fileIds) {
            String name = fileId;
            String hint = "文档，需要理解内容时调用 read_file(fileId)";
            try {
                FileVo meta = requireOwned(fileId, user);
                name = meta.getFileName();
                hint = manifestHint(kindOf(meta.getFileExt()), meta.getFileSize());
            } catch (Exception ignore) {
                // 归属校验在 buildBlocks/工具内统一处理
            }
            sb.append(index++).append(". ").append(name)
                    .append("（fileId=").append(fileId).append("；").append(hint).append("）；");
        }
        sb.append("文件内容分析类问题（总结、提取、翻译、审阅、图片理解等）直接基于附件作答，"
                + "不要为此检索或调用业务接口；作为业务附件提交时把 fileId 传给 FILE/FILE_ARRAY 字段；"
                + "需要“读文件 + 调接口”组合时，先用 read_file 提取信息，再用提取结果调用接口。"
                + "用途不明确时先向用户确认。");
        return sb.toString();
    }

    private String manifestHint(String kind, Long size) {
        if ("IMAGE".equals(kind) && size != null && size > MAX_IMAGE_BYTES) {
            return "图片超过 5MB，未随消息提供视觉内容，请提示用户压缩后重新上传";
        }
        return switch (kind) {
            case "IMAGE" -> "图片，视觉内容已随消息提供，可直接分析，不要调用 read_file";
            case "OTHER" -> "未知格式，可能无法提取文本，必要时提示用户下载查看";
            default -> "文档，需要理解内容时调用 read_file(fileId)";
        };
    }

    public void markSent(List<String> fileIds) {
        if (fileIds != null) {
            sentFiles.addAll(fileIds);
        }
    }

    /**
     * 构造随用户消息发送的 ContentBlock。
     * <p>
     * 只有图片转为 ImageBlock 直接提供视觉输入；其它文件不在此处读取或解析，
     * 仅通过 manifest 提供 fileId，由模型在需要理解内容时调用 read_file 触发按需解析。
     */
    public List<ContentBlock> buildBlocks(List<String> fileIds, AiUserContext user) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        if (fileIds.size() > MAX_BATCH_FILES) {
            throw new NwaySystemException("单次最多上传 " + MAX_BATCH_FILES + " 个文件");
        }
        List<ContentBlock> blocks = new ArrayList<>();
        for (String fileId : fileIds) {
            assertOwned(fileId, user);
            FileContent content = fileService.open(fileId);
            String ext = extOf(content.fileName());
            if (!"IMAGE".equals(kindOf(ext))) {
                continue;
            }
            try (InputStream in = content.openStream(0, -1)) {
                if (content.size() <= MAX_IMAGE_BYTES) {
                    String base64 = java.util.Base64.getEncoder().encodeToString(in.readAllBytes());
                    blocks.add(ImageBlock.builder()
                            .source(Base64Source.builder()
                                    .mediaType(StringUtils.defaultIfBlank(content.contentType(), "image/png"))
                                    .data(base64)
                                    .build())
                            .build());
                }
                blocks.add(TextBlock.builder().text("【图片附件】" + content.fileName()).build());
            } catch (Exception e) {
                blocks.add(TextBlock.builder().text("【图片附件】" + content.fileName() + "（读取失败）").build());
            }
        }
        markSent(fileIds);
        return blocks;
    }

    /**
     * 工具 read_file 使用：按需读取/补提取附件文本。
     * <p>
     * 工具在 reactor 线程执行，整个读取过程需显式恢复上传租户上下文，否则租户隔离会 fail-closed。
     */
    public FileText readText(String fileId, AiUserContext user, int maxChars) {
        String tenant = user == null ? null : user.effectiveTenantCode();
        if (StringUtils.isBlank(tenant)) {
            return readTextScoped(fileId, user, maxChars);
        }
        Object[] holder = new Object[1];
        com.nway.system.common.tenant.TenantUtil.runAsTenant(tenant,
                () -> holder[0] = readTextScoped(fileId, user, maxChars));
        return (FileText) holder[0];
    }

    private FileText readTextScoped(String fileId, AiUserContext user, int maxChars) {
        assertOwned(fileId, user);
        FileContent content = fileService.open(fileId);
        String cached = textCache.getIfPresent(fileId);
        if (cached != null) {
            return slice(content.fileName(), cached, maxChars);
        }
        String ext = extOf(content.fileName());
        try {
            String text = doExtract(content, ext);
            if (text != null) {
                textCache.put(fileId, text);
            }
            return slice(content.fileName(), text, maxChars);
        } catch (Exception e) {
            throw new NwaySystemException("附件解析失败：" + e.getMessage());
        }
    }

    public record FileText(String name, String text, boolean truncated) {
    }

    private FileText slice(String name, String text, int maxChars) {
        if (StringUtils.isBlank(text)) {
            return new FileText(name, null, false);
        }
        if (text.length() <= maxChars) {
            return new FileText(name, text, false);
        }
        return new FileText(name, text.substring(0, maxChars), true);
    }

    public String previewUrl(String fileId) {
        try {
            FileTicketVo ticket = fileService.createTicket(fileId, "preview");
            return ticket.getUrl();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 提取 ====================

    private String doExtract(FileContent content, String ext) throws Exception {
        String e = StringUtils.lowerCase(ext);
        if (TEXT_EXTENSIONS.contains(e)) {
            try (InputStream in = content.openStream(0, Math.min(content.size(), 2L * 1024 * 1024))) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return limit(text);
            }
        }
        if (IMAGE_EXTENSIONS.contains(e)) {
            return null;
        }
        if ("pdf".equals(e)) {
            byte[] bytes = readAll(content);
            try (PDDocument document = Loader.loadPDF(bytes)) {
                return limit(new PDFTextStripper().getText(document));
            }
        }
        if ("docx".equals(e)) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(readAll(content)));
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return limit(extractor.getText());
            }
        }
        if ("doc".equals(e)) {
            try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(readAll(content)));
                 WordExtractor extractor = new WordExtractor(document)) {
                return limit(extractor.getText());
            }
        }
        if ("xlsx".equals(e)) {
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(readAll(content)))) {
                return limit(readWorkbook(workbook));
            }
        }
        if ("xls".equals(e)) {
            try (Workbook workbook = new HSSFWorkbook(new ByteArrayInputStream(readAll(content)))) {
                return limit(readWorkbook(workbook));
            }
        }
        if ("pptx".equals(e)) {
            try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(readAll(content)))) {
                return limit(readSlides(slideShow));
            }
        }
        if ("ppt".equals(e)) {
            try (HSLFSlideShow slideShow = new HSLFSlideShow(new ByteArrayInputStream(readAll(content)))) {
                return limit(readSlides(slideShow));
            }
        }
        return null;
    }

    private String readWorkbook(Workbook workbook) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            sb.append("## ").append(sheet.getSheetName()).append('\n');
            int maxRow = Math.min(sheet.getLastRowNum(), 200);
            for (int r = 0; r <= maxRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                int maxCol = Math.min(row.getLastCellNum(), 30);
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < maxCol; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : cell.toString());
                }
                sb.append(String.join("\t", cells)).append('\n');
            }
            if (sb.length() > MAX_EXTRACT_CHARS) {
                break;
            }
        }
        return sb.toString();
    }

    private byte[] readAll(FileContent content) throws Exception {
        try (InputStream in = content.openStream(0, -1)) {
            return in.readAllBytes();
        }
    }

    private String readSlides(XMLSlideShow slideShow) {
        StringBuilder sb = new StringBuilder();
        for (XSLFSlide slide : slideShow.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    appendSlideText(sb, textShape.getText());
                }
            }
            if (sb.length() > MAX_EXTRACT_CHARS) {
                break;
            }
        }
        return sb.toString();
    }

    private String readSlides(HSLFSlideShow slideShow) {
        StringBuilder sb = new StringBuilder();
        for (HSLFSlide slide : slideShow.getSlides()) {
            for (HSLFShape shape : slide.getShapes()) {
                if (shape instanceof HSLFTextShape textShape) {
                    appendSlideText(sb, textShape.getText());
                }
            }
            if (sb.length() > MAX_EXTRACT_CHARS) {
                break;
            }
        }
        return sb.toString();
    }

    private void appendSlideText(StringBuilder sb, String text) {
        if (StringUtils.isNotBlank(text)) {
            sb.append(text.trim()).append('\n');
        }
    }

    private String limit(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > MAX_EXTRACT_CHARS ? trimmed.substring(0, MAX_EXTRACT_CHARS) : trimmed;
    }

    private AiFileVo toVo(FileVo vo) {
        AiFileVo result = new AiFileVo();
        result.setFileId(vo.getId());
        result.setName(vo.getFileName());
        result.setSize(vo.getFileSize());
        result.setMime(vo.getContentType());
        result.setKind(kindOf(vo.getFileExt()));
        result.setPreviewUrl(previewUrl(vo.getId()));
        return result;
    }

    private String kindOf(String ext) {
        String e = StringUtils.lowerCase(ext);
        if (IMAGE_EXTENSIONS.contains(e)) {
            return "IMAGE";
        }
        return switch (StringUtils.defaultString(e)) {
            case "pdf" -> "PDF";
            case "doc", "docx" -> "WORD";
            case "xls", "xlsx" -> "EXCEL";
            case "ppt", "pptx" -> "PPT";
            default -> TEXT_EXTENSIONS.contains(e) ? "TEXT" : "OTHER";
        };
    }

    private String extOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String summary(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }
}
