package com.nway.system.ai.service;

import com.nway.system.ai.export.AiExportDocxWriter;
import com.nway.system.ai.export.AiExportHtmlWriter;
import com.nway.system.ai.export.AiExportPptxWriter;
import com.nway.system.ai.export.AiExportXlsxWriter;
import com.nway.system.ai.web.model.AiExportRequest;
import com.nway.system.ai.web.model.AiExportVo;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.domain.FileTicket;
import com.nway.system.file.service.FileService;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AI 分析结果导出：把模型按用户导出意图组织的正文与 nway-view 结构转换为 docx / pptx / xlsx / html。
 * <p>
 * 生成文件复用文件中心的存储与短时票据下载链路；转换过程设置内容上限，避免大体积结果拖垮服务。
 *
 * @author nway
 */
@Slf4j
@Service
public class AiExportService {

    private static final Set<String> FORMATS = Set.of("docx", "pptx", "xlsx", "html");
    private static final int MAX_BLOCKS = 200;
    private static final int MAX_TEXT_CHARS = 200_000;
    private static final int MAX_TABLE_CELLS = 200_000;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_FILE_TITLE_LENGTH = 60;
    private static final String DEFAULT_TITLE = "AI 分析结果";
    private static final String BIZ_TYPE = "ai_export";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final FileService fileService;

    public AiExportService(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 重新签发导出文件的下载地址：历史消息里保存的票据会过期，读取历史时按 fileId 刷新。
     *
     * @return 新的下载地址；文件不存在或无权访问时返回 null
     */
    public String downloadUrl(String fileId) {
        if (StringUtils.isBlank(fileId)) {
            return null;
        }
        try {
            return fileService.createTicket(fileId, FileTicket.PURPOSE_DOWNLOAD).getUrl();
        } catch (Exception e) {
            log.debug("导出文件下载地址刷新失败 fileId={} error={}", fileId, e.getMessage());
            return null;
        }
    }

    public AiExportVo export(AiExportRequest request) {
        List<AiExportRequest.Block> blocks = request == null ? null : request.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            throw new NwaySystemException("没有可导出的分析内容");
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new NwaySystemException("导出内容过多，请缩小分析范围后重试");
        }
        String format = normalizeFormat(request.getFormat());
        validateSize(blocks);
        String title = normalizeTitle(request.getTitle());
        LocalDateTime now = LocalDateTime.now();

        byte[] content;
        try {
            content = switch (format) {
                case "docx" -> AiExportDocxWriter.write(request, title, now);
                case "pptx" -> AiExportPptxWriter.write(request, title, now);
                case "xlsx" -> AiExportXlsxWriter.write(request, title, now);
                default -> AiExportHtmlWriter.write(request, title, now);
            };
        } catch (NwaySystemException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 分析结果导出失败 format={}", format, e);
            throw new NwaySystemException("导出失败，请稍后重试");
        }

        String fileName = buildFileName(title, format, now);
        FileVo file = fileService.saveGenerated(content, fileName, BIZ_TYPE,
                StringUtils.left(request.getConversationId(), 64), "AI 分析结果导出");
        FileTicketVo ticket = fileService.createTicket(file.getId(), FileTicket.PURPOSE_DOWNLOAD);
        AiExportVo vo = new AiExportVo();
        vo.setFileId(file.getId());
        vo.setFileName(file.getFileName());
        vo.setSize(file.getFileSize());
        vo.setUrl(ticket.getUrl());
        log.info("AI 分析结果已导出 format={} fileId={} size={}", format, file.getId(), file.getFileSize());
        return vo;
    }

    private String normalizeFormat(String raw) {
        String format = StringUtils.lowerCase(StringUtils.trimToEmpty(raw), Locale.ROOT);
        if (format.startsWith(".")) {
            format = format.substring(1);
        }
        if (!FORMATS.contains(format)) {
            throw new NwaySystemException("不支持的导出格式，请选择 docx、pptx、xlsx 或 html");
        }
        return format;
    }

    private String normalizeTitle(String raw) {
        String title = StringUtils.trimToEmpty(raw);
        if (title.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }

    /**
     * 导出前统一校验内容规模：前端视图已按展示上限裁剪，这里再做一次服务端兜底，
     * 防止异常请求导致转换阶段内存与耗时失控。
     */
    private void validateSize(List<AiExportRequest.Block> blocks) {
        long textChars = 0;
        long cells = 0;
        for (AiExportRequest.Block block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.getText() != null) {
                textChars += block.getText().length();
            }
            AiExportRequest.View view = block.getView();
            if (view == null) {
                continue;
            }
            if (view.getText() != null) {
                textChars += view.getText().length();
            }
            if (view.getRows() != null) {
                cells += (long) view.getRows().size() * Math.max(1, view.effectiveColumns().size());
            }
            if (view.getList() != null) {
                cells += view.getList().size();
            }
            if (view.getItems() != null) {
                cells += view.getItems().size();
            }
            if (view.getFields() != null) {
                cells += view.getFields().size() * 2L;
            }
            if (view.getChart() != null && view.getChart().getSeries() != null) {
                cells += (long) view.getChart().getSeries().size()
                        * (view.getChart().getCategories() == null ? 0 : view.getChart().getCategories().size());
            }
            if (textChars > MAX_TEXT_CHARS || cells > MAX_TABLE_CELLS) {
                break;
            }
        }
        if (textChars > MAX_TEXT_CHARS) {
            throw new NwaySystemException("分析结果文本过长，请缩小分析范围后重试");
        }
        if (cells > MAX_TABLE_CELLS) {
            throw new NwaySystemException("分析结果数据量过大，请缩小查询范围后重试");
        }
    }

    private String buildFileName(String title, String format, LocalDateTime now) {
        String safe = title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        safe = safe.replaceAll("[.\\s]+$", "");
        if (safe.isEmpty()) {
            safe = "AI分析结果";
        }
        if (safe.length() > MAX_FILE_TITLE_LENGTH) {
            safe = safe.substring(0, MAX_FILE_TITLE_LENGTH);
        }
        return safe + "_" + FILE_TIME.format(now) + "." + format;
    }
}
