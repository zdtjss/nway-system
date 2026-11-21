package com.nway.system.authorization.service.resource;

import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 接口资源清单（启动时由 {@link ResourceManifestProvider} 构建）。
 * <p>
 * 每一项描述「一个 Controller 映射模板」需要哪个资源以及可选的动作集合；
 * 运行期由权限拦截器据此判定，不再按 URL 前缀硬编码推断。
 */
public final class ResourceManifest {

    /** 声明来源：注解 / 历史 URL 映射 / 登录态通用接口 / 未覆盖 */
    public enum Source {
        ANNOTATION, LEGACY, PUBLIC, UNCOVERED
    }

    /**
     * @param pattern      映射模板（如 /data/get/{formName}）
     * @param appCode      资源所属应用
     * @param resourceCode 资源标识；未覆盖时为 null
     * @param actions      可接受的动作集合（命中任一即放行）；空集合表示 fail-closed
     * @param source       声明来源
     */
    public record Entry(String pattern, String appCode, String resourceCode,
                        List<String> actions, Source source) {

        public boolean covered() {
            return source != Source.UNCOVERED && resourceCode != null;
        }
    }

    private final List<Entry> entries;
    private final Map<String, Entry> exact;
    private final List<PatternEntry> patterns;
    private final AntPathMatcher matcher = new AntPathMatcher();

    private record PatternEntry(String pattern, Entry entry) {
    }

    public ResourceManifest(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        Map<String, Entry> exactMap = new LinkedHashMap<>();
        List<PatternEntry> patternList = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.pattern().contains("{") || entry.pattern().contains("*")) {
                patternList.add(new PatternEntry(entry.pattern(), entry));
            } else {
                exactMap.put(entry.pattern(), entry);
            }
        }
        this.exact = Map.copyOf(exactMap);
        this.patterns = List.copyOf(patternList);
    }

    /** 按请求路径解析资源；无匹配返回 empty（调用方按 fail-closed 拒绝）。 */
    public Optional<Entry> resolve(String servletPath) {
        Entry direct = exact.get(servletPath);
        if (direct != null) {
            return Optional.of(direct);
        }
        for (PatternEntry pattern : patterns) {
            if (matcher.match(pattern.pattern(), servletPath)) {
                return Optional.of(pattern.entry());
            }
        }
        return Optional.empty();
    }

    public List<Entry> entries() {
        return entries;
    }

    public long count(Source source) {
        return entries.stream().filter(e -> e.source() == source).count();
    }

    /** 注解声明覆盖率（已注解 / 需要授权的映射总数）。 */
    public double annotationCoverage() {
        long declared = entries.stream().filter(e -> e.source() != Source.PUBLIC).count();
        if (declared == 0) {
            return 1.0;
        }
        return (double) count(Source.ANNOTATION) / declared;
    }
}
