package com.nway.system.file.web.admin;

import com.nway.system.file.web.support.FileHttpResponder;
import com.nway.system.file.domain.FileContent;
import com.nway.system.file.service.FileService;
import com.nway.system.file.domain.FileTicket;
import com.nway.system.common.security.PublicApi;
import com.nway.system.authorization.annotation.RequiresPermission;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.web.Result;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.web.model.FileQuery;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 内部文件接口（登录 JWT）。
 *
 * @author nway
 */
@RequiresPermission(resource = "file")
@RestController
@RequestMapping("file")
public class FileController {

    private final FileService fileService;

    private final FileHttpResponder httpResponder;

    public FileController(FileService fileService, FileHttpResponder httpResponder) {
        this.fileService = fileService;
        this.httpResponder = httpResponder;
    }

    @PublicApi(reason = "登录用户通用上传能力，租户隔离由实体保证")
    @PostMapping("upload")
    public Result<FileVo> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) String bizId,
                                 @RequestParam(required = false) String remark) {
        return Result.ok(fileService.upload(file, bizType, bizId, remark));
    }

    @RequiresPermission(resource = "file", action = "view")
    @GetMapping("queryPage")
    public Result<PageVo<FileVo>> queryPage(FileQuery query) {
        return Result.ok(fileService.queryPage(query));
    }

    @RequiresPermission(resource = "file", action = "view")
    @GetMapping("getById")
    public Result<FileVo> getById(@RequestParam String id) {
        return Result.ok(fileService.getById(id));
    }

    @PublicApi(reason = "登录用户通用下载能力")
    @GetMapping("download/{id}")
    public void download(@PathVariable String id, HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        FileContent content = openContent(id, response);
        if (content != null) {
            httpResponder.write(request, response, content, false);
        }
    }

    @PublicApi(reason = "登录用户通用预览能力")
    @GetMapping("preview/{id}")
    public void preview(@PathVariable String id, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        FileContent content = openContent(id, response);
        if (content != null) {
            httpResponder.write(request, response, content, true);
        }
    }

    @PublicApi(reason = "登录用户通用票据签发能力")
    @PostMapping("ticket")
    public Result<FileTicketVo> ticket(@RequestParam String id,
                                       @RequestParam(required = false, defaultValue = "preview") String purpose) {
        return Result.ok(fileService.createTicket(id, purpose));
    }

    /**
     * 浏览器直链（{@code <img>}/{@code <video>}）使用：匿名进入，凭票据访问单个文件。
     */
    @PublicApi(reason = "浏览器直链无法携带 Authorization Header，凭短时票据访问")
    @GetMapping("ticketDownload")
    public void ticketDownload(@RequestParam String ticket, HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        FileTicket fileTicket;
        FileContent content;
        try {
            fileTicket = fileService.resolveTicket(ticket);
            content = fileService.openForTicket(fileTicket);
        } catch (NwaySystemException e) {
            httpResponder.writeError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }
        boolean inline = FileTicket.PURPOSE_PREVIEW.equals(fileTicket.getPurpose());
        httpResponder.write(request, response, content, inline);
    }

    @RequiresPermission(resource = "file", action = "delete")
    @PostMapping("delete")
    public Result<Void> delete(@RequestParam String id) {
        fileService.delete(id);
        return Result.ok();
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
