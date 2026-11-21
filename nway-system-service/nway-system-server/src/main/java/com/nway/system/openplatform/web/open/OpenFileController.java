package com.nway.system.openplatform.web.open;

import com.nway.system.file.domain.FileContent;
import com.nway.system.file.service.FileService;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.web.support.FileHttpResponder;
import com.nway.system.file.web.model.FileQuery;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 开放平台文件接口（App Token）。
 * <p>
 * scope 由 {@code OpenApiInterceptor} 按“模块名 + HTTP 方法”推导：
 * GET → {@code file:read}，POST → {@code file:write}。
 *
 * @author nway
 */
@RestController
@RequestMapping("/open/file")
public class OpenFileController {

    private final FileService fileService;

    private final FileHttpResponder httpResponder;

    public OpenFileController(FileService fileService, FileHttpResponder httpResponder) {
        this.fileService = fileService;
        this.httpResponder = httpResponder;
    }

    @PostMapping("upload")
    public Result<FileVo> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) String bizId,
                                 @RequestParam(required = false) String remark) {
        return Result.ok(fileService.upload(file, bizType, bizId, remark));
    }

    @GetMapping("query")
    public Result<PageVo<FileVo>> query(FileQuery query) {
        return Result.ok(fileService.queryPage(query));
    }

    @GetMapping("get/{id}")
    public Result<FileVo> get(@PathVariable String id) {
        return Result.ok(fileService.getById(id));
    }

    @GetMapping("download/{id}")
    public void download(@PathVariable String id, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        FileContent content = openContent(id, response);
        if (content != null) {
            httpResponder.write(request, response, content, false);
        }
    }

    @GetMapping("preview/{id}")
    public void preview(@PathVariable String id, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        FileContent content = openContent(id, response);
        if (content != null) {
            httpResponder.write(request, response, content, true);
        }
    }

    @PostMapping("ticket")
    public Result<FileTicketVo> ticket(@RequestParam String id,
                                       @RequestParam(required = false, defaultValue = "preview") String purpose) {
        return Result.ok(fileService.createTicket(id, purpose));
    }

    private FileContent openContent(String id, HttpServletResponse response) throws IOException {
        try {
            return fileService.open(id);
        } catch (NwaySystemException e) {
            httpResponder.writeError(response, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return null;
        }
    }
}
