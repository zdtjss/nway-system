package com.nway.system.file.service;

import com.nway.system.file.domain.FileTicket;
import com.alibaba.fastjson2.JSON;
import com.nway.system.config.properties.FileProperties;
import com.nway.system.common.exception.NwaySystemException;
import com.nway.system.file.entity.SysFile;
import com.nway.system.file.web.model.FileTicketVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 文件短时票据服务。
 * <p>
 * 浏览器原生 {@code <img>}/{@code <video>}/{@code <a>} 无法携带 Authorization Header，
 * 因此签发短时、绑定单文件的 Redis 票据；票据有效期内可重复使用（视频 Range 会多次请求）。
 *
 * @author nway
 */
@Service
public class FileTicketService {

    private static final String KEY_PREFIX = "nway:file:ticket:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisTemplate<String, Object> redisTemplate;

    private final FileProperties properties;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public FileTicketService(RedisTemplate<String, Object> redisTemplate, FileProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public FileTicketVo create(SysFile file, String purpose) {
        String ticket = randomTicket();
        FileTicket value = new FileTicket();
        value.setFileId(file.getId());
        value.setTenantCode(file.getTenantCode());
        value.setEmployeeId(file.getCreatorId());
        value.setPurpose(normalizePurpose(purpose));
        long ttl = ttlSeconds();
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, JSON.toJSONString(value), Duration.ofSeconds(ttl));
        String url = contextPath + "/file/ticketDownload?ticket=" + ticket;
        return new FileTicketVo(ticket, url, ttl);
    }

    /**
     * 解析票据；不存在/已过期/格式损坏统一视为无效。
     */
    public FileTicket resolve(String ticket) {
        if (StringUtils.isBlank(ticket)) {
            throw new NwaySystemException("缺少文件票据");
        }
        Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + ticket);
        if (cached == null) {
            throw new NwaySystemException("票据无效或已过期");
        }
        try {
            FileTicket value = JSON.parseObject(String.valueOf(cached), FileTicket.class);
            if (value == null || StringUtils.isBlank(value.getFileId()) || StringUtils.isBlank(value.getTenantCode())) {
                throw new NwaySystemException("票据无效或已过期");
            }
            return value;
        } catch (NwaySystemException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new NwaySystemException("票据无效或已过期");
        }
    }

    public boolean isPreview(FileTicket ticket) {
        return FileTicket.PURPOSE_PREVIEW.equals(ticket.getPurpose());
    }

    private String normalizePurpose(String purpose) {
        if (StringUtils.isBlank(purpose)) {
            return FileTicket.PURPOSE_PREVIEW;
        }
        String value = purpose.trim().toLowerCase();
        if (!FileTicket.PURPOSE_PREVIEW.equals(value) && !FileTicket.PURPOSE_DOWNLOAD.equals(value)) {
            throw new NwaySystemException("不支持的票据用途：" + purpose);
        }
        return value;
    }

    private long ttlSeconds() {
        long ttl = properties.getTicketTtlSeconds();
        return ttl > 0 ? ttl : 600;
    }

    private String randomTicket() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
