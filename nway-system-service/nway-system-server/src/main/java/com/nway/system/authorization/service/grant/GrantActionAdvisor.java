package com.nway.system.authorization.service.grant;

import com.alibaba.fastjson2.JSON;
import com.nway.system.authorization.dao.ResourceDao;
import com.nway.system.authorization.entity.SysResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权动作组合告警。
 * <p>
 * 「写操作」通常需要「读」才能定位数据：只授 {@code write/delete/export/import} 而不授
 * {@code read}，业务上几乎总是误配（如只勾了“写入”没勾“读取”），但系统只会在调用读接口
 * （{@code /data/metadata}、列表查询等）时表现为 403，排查成本高。因此写入授权时主动告警。
 * <p>
 * <b>只告警不阻断</b>：个别资源确实只有写语义（如 {@code wform:dict} 只登记了 write），
 * 故以「资源目录是否声明 read」为准，避免误报；通配资源 {@code *} 不参与判断。
 */
@Component
public class GrantActionAdvisor {

    private static final Logger log = LoggerFactory.getLogger(GrantActionAdvisor.class);

    /** 需要读能力配合的写类动作（与 wform 能力资源的动作词表一致）。 */
    private static final Set<String> WRITE_FAMILY = Set.of("write", "delete", "export", "import");
    /** 全部动作通配：显式授予全部动作时不告警。 */
    private static final String ALL_ACTIONS = "*";
    /** 读动作。 */
    private static final String READ = "read";
    /** 资源通配：表示应用全部资源，不参与目录校验。 */
    private static final String WILDCARD_RESOURCE = "*";

    private final ResourceDao resourceDao;

    public GrantActionAdvisor(ResourceDao resourceDao) {
        this.resourceDao = resourceDao;
    }

    /**
     * 写类动作缺少 read 时打 WARN（不阻断写入）。
     *
     * @param appCode      应用标识
     * @param resourceCode 资源标识；{@code *} 或空不校验
     * @param actionsJson  动作 JSON 数组，如 {@code ["write"]}
     */
    public void warnIfWriteWithoutRead(String appCode, String resourceCode, String actionsJson) {
        if (!isWriteWithoutRead(appCode, resourceCode, actionsJson)) {
            return;
        }
        log.warn("授权缺少 read 动作：appCode={}, resourceCode={}, actions={} —— "
                        + "写类动作已授予但读能力缺失，读接口（元数据/列表查询）会 403；"
                        + "请确认是否应补 read，或该资源确实只需写能力",
                appCode, resourceCode, actionsJson);
    }

    /**
     * 判定是否属于“有写无读”：仅当动作含写类、不含 read / {@code *}，
     * 且资源目录确实声明了 read 时才成立。
     */
    public boolean isWriteWithoutRead(String appCode, String resourceCode, String actionsJson) {
        Set<String> actions = parse(actionsJson);
        if (actions.isEmpty() || actions.contains(ALL_ACTIONS) || actions.contains(READ)) {
            return false;
        }
        if (actions.stream().noneMatch(WRITE_FAMILY::contains)) {
            return false;
        }
        if (resourceCode == null || resourceCode.isBlank() || WILDCARD_RESOURCE.equals(resourceCode)) {
            return false;
        }
        SysResource resource = resourceDao.getByAppAndCode(appCode, resourceCode);
        // 目录未声明 read：可能本就是只写资源，不告警
        return resource != null && resourceDao.getAction(resource.getId(), READ) != null;
    }

    private static Set<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = JSON.parseArray(json, String.class);
            return list == null ? Set.of() : new LinkedHashSet<>(list);
        } catch (RuntimeException e) {
            // 动作 JSON 损坏由写入校验负责（GrantCommandService.validate 会抛业务异常）
            return Set.of();
        }
    }
}
