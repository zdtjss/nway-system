package com.nway.system.file.web.support;

import com.nway.system.file.domain.FileContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件响应输出：Content-Disposition、Content-Type、HTTP Range（206/416）。
 * <p>
 * 视频播放必须支持 Range，否则浏览器无法拖动进度。
 *
 * @author nway
 */
@Component
public class FileHttpResponder {

    private static final String HEADER_RANGE = "Range";

    private static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";

    private static final String HEADER_CONTENT_RANGE = "Content-Range";

    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

    private static final String HEADER_NOSNIFF = "X-Content-Type-Options";

    public void write(HttpServletRequest request, HttpServletResponse response,
                      FileContent content, boolean inline) throws IOException {
        long total = content.size();
        long start = 0;
        long end = total - 1;
        boolean partial = false;

        String rangeHeader = request.getHeader(HEADER_RANGE);
        if (StringUtils.isNotBlank(rangeHeader)) {
            if (total <= 0) {
                writeUnsatisfiable(response, total);
                return;
            }
            HttpRange range = parseFirstRange(rangeHeader);
            if (range == null) {
                writeUnsatisfiable(response, total);
                return;
            }
            try {
                start = range.getRangeStart(total);
                end = range.getRangeEnd(total);
            } catch (IllegalArgumentException e) {
                writeUnsatisfiable(response, total);
                return;
            }
            if (start > end) {
                writeUnsatisfiable(response, total);
                return;
            }
            partial = true;
        }

        long length = total <= 0 ? 0 : end - start + 1;
        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        response.setHeader(HEADER_ACCEPT_RANGES, "bytes");
        if (partial) {
            response.setHeader(HEADER_CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
        }
        response.setContentType(content.contentType());
        response.setHeader(HEADER_NOSNIFF, "nosniff");
        response.setHeader(HEADER_CONTENT_DISPOSITION,
                contentDisposition(content.fileName(), inline));
        response.setContentLengthLong(length);
        if (length == 0) {
            return;
        }
        try (InputStream in = content.openStream(start, length)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    /**
     * 以真实 HTTP 状态返回文件错误（浏览器 &lt;img&gt;/&lt;video&gt; 与 SDK 下载都依赖状态码判断失败）。
     */
    public void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader(HEADER_NOSNIFF, "nosniff");
        response.getWriter().write("{\"code\":" + status + ",\"msg\":"
                + com.alibaba.fastjson2.JSON.toJSONString(message) + "}");
    }

    private HttpRange parseFirstRange(String rangeHeader) {
        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            return ranges.isEmpty() ? null : ranges.getFirst();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void writeUnsatisfiable(HttpServletResponse response, long total) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader(HEADER_CONTENT_RANGE, "bytes */" + total);
        response.setContentLength(0);
    }

    /**
     * RFC 5987：中文文件名同时提供 ASCII 回退名与 {@code filename*}。
     */
    private String contentDisposition(String fileName, boolean inline) {
        String type = inline ? "inline" : "attachment";
        String name = fileName == null ? "file" : fileName;
        String fallback = name.replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\\", "_")
                .replace("\"", "_");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
        return type + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
