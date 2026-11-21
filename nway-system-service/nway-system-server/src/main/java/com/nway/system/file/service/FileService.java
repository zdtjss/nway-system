package com.nway.system.file.service;

import com.nway.system.file.domain.FileContent;
import com.nway.system.file.domain.FileTicket;
import com.nway.system.file.support.FileStorage;
import com.nway.system.file.support.FileStorageRegistry;
import com.nway.system.file.support.FileTypePolicy;
import com.nway.system.file.support.StorageKeyGenerator;
import com.nway.spring.jdbc.pagination.Page;
import com.nway.system.common.web.PageVo;
import com.nway.system.common.security.LoginUser;
import com.nway.system.common.enums.ErrorCodeEnum;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.common.security.LoginUserContext;
import com.nway.system.common.tenant.TenantUtil;
import com.nway.system.file.dao.SysFileDao;
import com.nway.system.file.entity.SysFile;
import com.nway.system.file.web.model.FileQuery;
import com.nway.system.file.web.model.FileTicketVo;
import com.nway.system.file.web.model.FileVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 文件中心业务服务：上传编排、存储路由、元数据查询、删除与票据。
 *
 * @author nway
 */
@Slf4j
@Service
public class FileService {

    private static final int MAX_BIZ_TYPE_LENGTH = 64;

    private static final int MAX_BIZ_ID_LENGTH = 64;

    private static final int MAX_REMARK_LENGTH = 255;

    private static final int MAX_OPERATOR_LENGTH = 10;

    private final SysFileDao fileDao;

    private final FileStorageRegistry storageRegistry;

    private final FileTypePolicy fileTypePolicy;

    private final StorageKeyGenerator storageKeyGenerator;

    private final FileTicketService ticketService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public FileService(SysFileDao fileDao, FileStorageRegistry storageRegistry,
                       FileTypePolicy fileTypePolicy, StorageKeyGenerator storageKeyGenerator,
                       FileTicketService ticketService) {
        this.fileDao = fileDao;
        this.storageRegistry = storageRegistry;
        this.fileTypePolicy = fileTypePolicy;
        this.storageKeyGenerator = storageKeyGenerator;
        this.ticketService = ticketService;
    }

    /**
     * 单文件上传：先落盘，DB 写入失败时补偿删除（文件 IO 与事务无法强一致）。
     */
    public FileVo upload(MultipartFile file, String bizType, String bizId, String remark) {
        FileTypePolicy.Validated validated = fileTypePolicy.validate(file);
        String tenantCode = TenantUtil.currentTenantCode();
        if (StringUtils.isBlank(tenantCode)) {
            throw new NwaySystemException("无法确定当前租户");
        }
        String safeBizType = validateText(bizType, MAX_BIZ_TYPE_LENGTH, "业务类型");
        String safeBizId = validateText(bizId, MAX_BIZ_ID_LENGTH, "业务单据ID");
        String safeRemark = validateText(remark, MAX_REMARK_LENGTH, "备注");

        String storageKey = storageKeyGenerator.generate(tenantCode, validated.ext());
        FileStorage storage = storageRegistry.defaultStorage();
        MessageDigest digest = sha256Digest();
        try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
            storage.store(storageKey, in, validated.size(), validated.contentType());
        } catch (IOException e) {
            throw new NwaySystemException("文件写入失败", e);
        }

        SysFile entity = new SysFile();
        entity.setFileName(validated.fileName());
        entity.setStorageType(storage.type());
        entity.setStorageKey(storageKey);
        entity.setFileSize(validated.size());
        entity.setContentType(validated.contentType());
        entity.setFileExt(validated.ext());
        entity.setSha256(HexFormat.of().formatHex(digest.digest()));
        entity.setBizType(safeBizType);
        entity.setBizId(safeBizId);
        entity.setRemark(safeRemark);
        fillOperator(entity);
        try {
            fileDao.insert(entity);
        } catch (RuntimeException e) {
            compensateDelete(storage, storageKey);
            throw e;
        }
        return toVo(entity);
    }

    public PageVo<FileVo> queryPage(FileQuery query) {
        Page<SysFile> page = fileDao.queryPage(query);
        List<FileVo> data = page.getPageData().stream().map(this::toVo).toList();
        return new PageVo<>(data, page.getTotal());
    }

    public FileVo getById(String id) {
        return toVo(requireFile(id));
    }

    /**
     * 按文件记录上的 {@code storage_type} 路由读取（存量 local 文件在切换默认存储后仍可读）。
     */
    public FileContent open(String id) {
        SysFile entity = requireFile(id);
        FileStorage storage = storageRegistry.get(entity.getStorageType());
        return new FileContent(entity, storage);
    }

    public FileTicketVo createTicket(String id, String purpose) {
        return ticketService.create(requireFile(id), purpose);
    }

    public FileTicket resolveTicket(String ticket) {
        return ticketService.resolve(ticket);
    }

    /**
     * 按票据恢复租户上下文后读取文件，并校验文件确实归属于票据记录的租户。
     */
    public FileContent openForTicket(FileTicket ticket) {
        SysFile[] holder = new SysFile[1];
        TenantUtil.runAsTenant(ticket.getTenantCode(), () -> holder[0] = fileDao.queryById(ticket.getFileId()));
        SysFile entity = holder[0];
        if (entity == null || !Strings.CS.equals(entity.getTenantCode(), ticket.getTenantCode())) {
            throw new NwaySystemException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "文件不存在或无权访问");
        }
        FileStorage storage = storageRegistry.get(entity.getStorageType());
        return new FileContent(entity, storage);
    }

    /**
     * 逻辑删除元数据；物理文件保留（由后续清理任务统一处理）。
     */
    public void delete(String id) {
        requireFile(id);
        fileDao.deleteById(id);
    }

    // ==================== Private ====================

    private SysFile requireFile(String id) {
        if (StringUtils.isBlank(id)) {
            throw new NwaySystemException(ErrorCodeEnum.BAD_REQUEST, "文件ID不能为空");
        }
        SysFile entity = fileDao.queryById(id);
        if (entity == null) {
            throw new NwaySystemException(ErrorCodeEnum.RESOURCE_NOT_FOUND, "文件不存在或无权访问");
        }
        return entity;
    }

    private FileVo toVo(SysFile entity) {
        FileVo vo = new FileVo();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setContentType(entity.getContentType());
        vo.setFileExt(entity.getFileExt());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setRemark(entity.getRemark());
        vo.setCreatorId(entity.getCreatorId());
        vo.setCreatorName(entity.getCreatorName());
        vo.setCreateTime(entity.getCreateTime());
        vo.setDownloadUrl(contextPath + "/file/download/" + entity.getId());
        vo.setPreviewUrl(contextPath + "/file/preview/" + entity.getId());
        return vo;
    }

    /**
     * 审计列 creator_id/modifier_id 为 varchar(10)，开放平台无代理用户时的占位工号可能超长，
     * 这里显式赋值避免 DB 写入失败（显式值不会再被 fillStrategy 覆盖）。
     */
    private void fillOperator(SysFile entity) {
        LoginUser user = LoginUserContext.current();
        if (user == null || StringUtils.isBlank(user.getEmployeeId())) {
            return;
        }
        String employeeId = user.getEmployeeId();
        String operator = employeeId.length() > MAX_OPERATOR_LENGTH
                ? employeeId.substring(0, MAX_OPERATOR_LENGTH) : employeeId;
        entity.setCreatorId(operator);
        entity.setModifierId(operator);
    }

    private String validateText(String value, int maxLength, String label) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new NwaySystemException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private void compensateDelete(FileStorage storage, String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (IOException ignore) {
            log.warn("文件写入补偿删除失败 key={}", storageKey);
        }
    }
}
