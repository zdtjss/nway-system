package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiFieldDef;
import com.nway.system.ai.domain.AiInterfaceConfig;
import com.nway.system.ai.domain.AiUserContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口检索：规则召回 + 大模型语义路由的两级检索。
 * <p>
 * 第一级用字段加权、IDF 与中文二元组做低成本召回；当最高分低于阈值时，
 * 说明词面匹配不可靠，再让大模型在「当前用户可见」的接口目录中做语义路由。
 * 模型只参与排序，结果仍要经过发布态与接口权限过滤，不直接执行任何操作。
 *
 * @author nway
 */
@Service
public class AiInterfaceSearchService {

    /** 规则检索最高分低于该值时，词面匹配已不可靠，触发大模型语义路由 */
    public static final double CONFIDENT_SCORE = 0.50;

    /** 低于该分值视为无关联，避免弱命中污染候选列表 */
    private static final double MIN_SCORE = 0.18;

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;
    private static final int MAX_OPTION_LABELS = 20;

    /**
     * 中文近义词归一化：用户口语与接口元数据用词常常不同（员工/人员/用户），
     * 统一到同一说法后再匹配。映射值不能同时作为键，避免替换链。
     */
    private static final Map<String, String> SYNONYMS = synonyms();

    /** 英文别名按词边界替换，避免 user/list 等短词破坏其他英文单词 */
    private static final Map<String, Pattern> ASCII_SYNONYM_PATTERNS = asciiSynonymPatterns();

    /** 问句前缀：只从片段开头剥离，避免「申请」「信息中心」这类词被误伤 */
    private static final List<String> PREFIX_NOISE = sortedNoise(
            "查一下", "查一查", "查询", "查看", "查找", "搜索", "检索", "获取", "得到",
            "列出", "显示", "展示", "看一下", "看看", "帮我", "帮忙", "麻烦", "给我",
            "我要", "我想", "需要", "想要", "请帮我", "请帮忙", "系统中", "系统", "平台",
            "所有", "全部", "相关", "有哪些", "有什么");

    /** 问句后缀：只从片段结尾剥离 */
    private static final List<String> SUFFIX_NOISE = sortedNoise(
            "相关信息", "一下", "一些", "相关", "信息", "数据",
            "怎么样", "有哪些", "是什么", "是多少", "有什么", "吗", "呢", "吧");

    /** 写操作动词：用于和接口 riskLevel 做动作对齐 */
    private static final List<String> WRITE_VERBS = List.of(
            "新增", "新建", "创建", "添加", "修改", "更新", "编辑",
            "删除", "移除", "提交", "发起", "上报", "审批", "通过", "驳回",
            "撤销", "启用", "停用", "导入");

    /** 读操作动词：用于和接口 riskLevel 做动作对齐 */
    private static final List<String> READ_VERBS = List.of(
            "查询", "查看", "获取", "列表", "名单", "详情", "明细", "统计", "导出", "搜索", "查找");

    private static final Pattern SEPARATORS =
            Pattern.compile("[\\s,，。；;：:、/\\\\|（）()\\[\\]{}]+");
    /** 连续中文按字取二元组，连续英文/数字整体作为一个词 */
    private static final Pattern SEGMENTS = Pattern.compile("[\\u4e00-\\u9fff]+|[a-z0-9_]+");

    private final AiInterfaceService interfaceService;
    private final AiPermissionService permissionService;
    private final AiIntentRouter intentRouter;

    public AiInterfaceSearchService(AiInterfaceService interfaceService,
                                    AiPermissionService permissionService,
                                    AiIntentRouter intentRouter) {
        this.interfaceService = interfaceService;
        this.permissionService = permissionService;
        this.intentRouter = intentRouter;
    }

    public record ScoredConfig(AiInterfaceConfig config, double score) {
    }

    public List<ScoredConfig> search(AiUserContext user, String query, String appCode, int topK) {
        int limit = clampTopK(topK);
        String text = StringUtils.trimToEmpty(query);
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        List<AiInterfaceConfig> configs = allowedConfigs(user, appCode);
        if (configs.isEmpty()) {
            return List.of();
        }

        List<ScoredConfig> lexical = rank(configs, canonicalize(text));
        double best = lexical.isEmpty() ? 0 : lexical.get(0).score();
        if (best >= CONFIDENT_SCORE) {
            return limit(lexical, limit);
        }

        // 词面召回不可靠：让模型在已授权目录中按语义重新路由，失败时保留规则结果
        List<String> routed = intentRouter.route(user, text, configs, Math.max(limit, 3));
        if (routed.isEmpty()) {
            return limit(lexical, limit);
        }
        return merge(routed, lexical, configs, limit);
    }

    /**
     * 当前用户可见且已发布的候选接口：租户过滤 + appCode 过滤 + 接口级权限过滤。
     */
    private List<AiInterfaceConfig> allowedConfigs(AiUserContext user, String appCode) {
        List<AiInterfaceConfig> configs = interfaceService.getPublishedConfigs(
                user == null ? null : user.effectiveTenantCode());
        String app = StringUtils.trimToEmpty(appCode);
        List<AiInterfaceConfig> allowed = new ArrayList<>();
        for (AiInterfaceConfig config : configs) {
            if (!app.isEmpty() && !app.equalsIgnoreCase(StringUtils.trimToEmpty(config.getAppCode()))) {
                continue;
            }
            if (!permissionService.isInterfaceAllowed(user, config)) {
                continue;
            }
            allowed.add(config);
        }
        return allowed;
    }

    /**
     * 规则排序：IDF 降低“列表/信息”等高频词权重，字段权重体现名称/关键词的优先级，
     * 二元组覆盖率保证只命中一个泛词的接口不会排到前面。
     */
    private List<ScoredConfig> rank(List<AiInterfaceConfig> configs, String query) {
        ParsedQuery parsed = parseQuery(query);
        if (parsed.grams().isEmpty() && parsed.phrases().isEmpty()) {
            return List.of();
        }
        List<Document> docs = new ArrayList<>(configs.size());
        for (AiInterfaceConfig config : configs) {
            docs.add(new Document(config));
        }
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String term : parsed.terms()) {
            int count = 0;
            for (Document doc : docs) {
                if (doc.blob.contains(term)) {
                    count++;
                }
            }
            documentFrequency.put(term, count);
        }

        int total = docs.size();
        List<ScoredConfig> scored = new ArrayList<>();
        for (Document doc : docs) {
            double raw = 0;
            int matched = 0;
            for (String gram : parsed.grams()) {
                double hit = doc.termWeight(gram);
                if (hit > 0) {
                    matched++;
                    raw += idf(gram, total, documentFrequency) * hit;
                }
            }
            double phraseRaw = 0;
            for (String phrase : parsed.phrases()) {
                double hit = doc.phraseWeight(phrase);
                if (hit > 0) {
                    phraseRaw = Math.max(phraseRaw, idf(phrase, total, documentFrequency) * hit);
                }
            }
            double coverage = parsed.grams().isEmpty()
                    ? 0 : (double) matched / parsed.grams().size();
            double termScore = 1 - Math.exp(-raw / 12.0);
            double phraseScore = 1 - Math.exp(-phraseRaw / 8.0);
            double score = 0.55 * termScore + 0.30 * coverage + 0.15 * phraseScore;
            if (parsed.action().matches(doc.config)) {
                score += 0.05;
            }
            score = Math.max(score, doc.exactBoost(parsed));
            score = Math.min(1.0, score);
            if (score >= MIN_SCORE) {
                scored.add(new ScoredConfig(doc.config, score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredConfig::score).reversed());
        return scored;
    }

    /**
     * 模型路由结果置顶，再补充规则召回；模型返回的编码必须存在于已授权目录。
     */
    private List<ScoredConfig> merge(List<String> routedCodes, List<ScoredConfig> lexical,
                                     List<AiInterfaceConfig> configs, int limit) {
        Map<String, AiInterfaceConfig> byCode = new LinkedHashMap<>();
        for (AiInterfaceConfig config : configs) {
            byCode.put(config.getInterfaceCode(), config);
        }
        Map<String, Double> lexicalScores = new HashMap<>();
        for (ScoredConfig scored : lexical) {
            lexicalScores.put(scored.config().getInterfaceCode(), scored.score());
        }
        Map<String, ScoredConfig> merged = new LinkedHashMap<>();
        for (String code : routedCodes) {
            AiInterfaceConfig config = byCode.get(code);
            if (config == null) {
                continue;
            }
            merged.put(code, new ScoredConfig(config,
                    Math.max(0.90, lexicalScores.getOrDefault(code, 0.0))));
        }
        for (ScoredConfig scored : lexical) {
            merged.putIfAbsent(scored.config().getInterfaceCode(), scored);
        }
        return limit(new ArrayList<>(merged.values()), limit);
    }

    private static List<ScoredConfig> limit(List<ScoredConfig> scored, int limit) {
        return scored.stream().limit(limit).toList();
    }

    private static int clampTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private static double idf(String term, int total, Map<String, Integer> documentFrequency) {
        int df = documentFrequency.getOrDefault(term, 0);
        return Math.log(1 + (total - df + 0.5) / (df + 0.5));
    }

    // ==================== 查询解析 ====================

    private record ParsedQuery(String text, String cleaned, List<String> grams,
                               List<String> phrases, List<String> terms, Action action) {
    }

    private static ParsedQuery parseQuery(String canonical) {
        String cleaned = clean(canonical);
        List<String> grams = grams(cleaned);
        List<String> phrases = phrases(cleaned);
        LinkedHashSet<String> terms = new LinkedHashSet<>(grams);
        terms.addAll(phrases);
        return new ParsedQuery(canonical, cleaned, grams, phrases,
                new ArrayList<>(terms), action(canonical));
    }

    /**
     * 去掉问句前后缀噪声，保留业务名词。例如「查询人员列表信息」→「人员列表」。
     */
    private static String clean(String canonical) {
        List<String> parts = new ArrayList<>();
        for (String segment : SEPARATORS.split(canonical)) {
            String value = stripNoise(segment);
            if (value.length() >= 2) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
    }

    private static String stripNoise(String segment) {
        String value = segment.trim();
        boolean changed = true;
        while (changed && value.length() > 1) {
            changed = false;
            for (String noise : PREFIX_NOISE) {
                if (value.startsWith(noise) && value.length() > noise.length()) {
                    value = value.substring(noise.length());
                    changed = true;
                    break;
                }
            }
            if (changed) {
                continue;
            }
            for (String noise : SUFFIX_NOISE) {
                if (value.endsWith(noise) && value.length() > noise.length()) {
                    value = value.substring(0, value.length() - noise.length());
                    changed = true;
                    break;
                }
            }
        }
        return value.trim();
    }

    private static List<String> grams(String text) {
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        Matcher matcher = SEGMENTS.matcher(text);
        while (matcher.find()) {
            String segment = matcher.group();
            if (segment.length() < 2) {
                continue;
            }
            if (isAscii(segment)) {
                grams.add(segment);
                continue;
            }
            for (int i = 0; i + 2 <= segment.length(); i++) {
                grams.add(segment.substring(i, i + 2));
            }
        }
        return new ArrayList<>(grams);
    }

    /**
     * 短语用于精确子串加分：整段中文 + 长中文片段的 4 字窗口，
     * 使「nway人员列表」「华南区的人员列表」都能命中名称中的「人员列表」。
     */
    private static List<String> phrases(String cleaned) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        for (String segment : SEPARATORS.split(cleaned)) {
            Matcher matcher = SEGMENTS.matcher(segment);
            while (matcher.find()) {
                String part = matcher.group();
                if (part.length() < 2 || isAscii(part)) {
                    continue;
                }
                phrases.add(part);
                if (part.length() > 4) {
                    for (int i = 0; i + 4 <= part.length(); i++) {
                        phrases.add(part.substring(i, i + 4));
                    }
                }
            }
        }
        return new ArrayList<>(phrases);
    }

    private static Action action(String canonical) {
        boolean write = containsAny(canonical, WRITE_VERBS);
        boolean read = containsAny(canonical, READ_VERBS);
        if (write && !read) {
            return Action.WRITE;
        }
        if (read && !write) {
            return Action.READ;
        }
        return Action.NONE;
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private enum Action {
        READ, WRITE, NONE;

        /** 动作与接口风险等级一致时给微弱加分，不做反向惩罚，避免误伤名词用法 */
        boolean matches(AiInterfaceConfig config) {
            return switch (this) {
                case READ -> !config.isWrite();
                case WRITE -> config.isWrite();
                case NONE -> false;
            };
        }
    }

    // ==================== 字段与分值 ====================

    private static final class Document {

        final AiInterfaceConfig config;
        final String code;
        final String name;
        final String description;
        final String keywords;
        final String labels;
        final String examples;
        final String path;
        final String appCode;
        final String blob;

        Document(AiInterfaceConfig config) {
            this.config = config;
            this.code = canonicalize(config.getInterfaceCode());
            this.name = canonicalize(config.getName());
            this.description = canonicalize(config.getDescription());
            this.keywords = canonicalize(config.getSearchKeywords());
            this.labels = canonicalize(labels(config));
            this.examples = canonicalize(StringUtils.join(config.getExamples(), " "));
            this.path = canonicalize(config.getPath());
            this.appCode = canonicalize(config.getAppCode());
            this.blob = String.join(" ", code, name, description, keywords,
                    labels, examples, path, appCode);
        }

        double termWeight(String term) {
            double weight = 0;
            if (name.contains(term)) {
                weight += 6;
            }
            if (keywords.contains(term)) {
                weight += 4;
            }
            if (code.contains(term)) {
                weight += 3;
            }
            if (description.contains(term)) {
                weight += 2;
            }
            if (labels.contains(term)) {
                weight += 1.5;
            }
            if (examples.contains(term)) {
                weight += 1.2;
            }
            if (path.contains(term) || appCode.contains(term)) {
                weight += 0.8;
            }
            if (weight == 0 && blob.contains(term)) {
                weight += 0.3;
            }
            return weight;
        }

        double phraseWeight(String phrase) {
            if (name.contains(phrase)) {
                return 5;
            }
            if (keywords.contains(phrase)) {
                return 3.5;
            }
            if (description.contains(phrase)) {
                return 2.5;
            }
            if (labels.contains(phrase)) {
                return 1.5;
            }
            if (blob.contains(phrase)) {
                return 0.8;
            }
            return 0;
        }

        double exactBoost(ParsedQuery query) {
            if (code.equals(query.text()) || name.equals(query.text())) {
                return 1.0;
            }
            if (query.cleaned().length() >= 3 && name.contains(query.cleaned())) {
                return 0.95;
            }
            if (query.cleaned().length() >= 3 && keywords.contains(query.cleaned())) {
                return 0.90;
            }
            if (query.text().length() >= 3 && name.contains(query.text())) {
                return 0.90;
            }
            if (query.cleaned().length() >= 3 && description.contains(query.cleaned())) {
                return 0.82;
            }
            if (query.text().length() >= 3 && keywords.contains(query.text())) {
                return 0.85;
            }
            if (query.text().length() >= 3 && description.contains(query.text())) {
                return 0.75;
            }
            if (query.text().length() >= 3 && blob.contains(query.text())) {
                return 0.65;
            }
            return 0;
        }
    }

    private static String labels(AiInterfaceConfig config) {
        StringBuilder sb = new StringBuilder();
        appendFields(sb, config.getInputSchema());
        return sb.toString();
    }

    private static void appendFields(StringBuilder sb, List<AiFieldDef> fields) {
        if (fields == null) {
            return;
        }
        for (AiFieldDef field : fields) {
            if (StringUtils.isNotBlank(field.getLabel())) {
                sb.append(field.getLabel()).append(' ');
            }
            if (StringUtils.isNotBlank(field.getDescription())) {
                sb.append(field.getDescription()).append(' ');
            }
            if (field.getOptions() != null) {
                int shown = 0;
                for (AiFieldDef.AiOption option : field.getOptions()) {
                    if (shown++ >= MAX_OPTION_LABELS) {
                        break;
                    }
                    if (option != null && StringUtils.isNotBlank(option.getLabel())) {
                        sb.append(option.getLabel()).append(' ');
                    }
                }
            }
            appendFields(sb, field.getProperties());
        }
    }

    // ==================== 文本归一化 ====================

    private static String canonicalize(String text) {
        String value = Normalizer.normalize(StringUtils.defaultString(text), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : SYNONYMS.entrySet()) {
            if (isAscii(entry.getKey())) {
                value = ASCII_SYNONYM_PATTERNS.get(entry.getKey()).matcher(value)
                        .replaceAll(entry.getValue());
            } else {
                value = value.replace(entry.getKey(), entry.getValue());
            }
        }
        return value.trim();
    }

    private static Map<String, Pattern> asciiSynonymPatterns() {
        Map<String, Pattern> patterns = new HashMap<>();
        for (String key : SYNONYMS.keySet()) {
            if (isAscii(key)) {
                patterns.put(key, Pattern.compile(
                        "(?<![a-z0-9])" + Pattern.quote(key) + "(?![a-z0-9])"));
            }
        }
        return Collections.unmodifiableMap(patterns);
    }

    private static boolean isAscii(String text) {
        return text.chars().allMatch(ch -> ch < 128);
    }

    private static Map<String, String> synonyms() {
        Map<String, String> map = new LinkedHashMap<>();
        putAll(map, "人员", "员工", "职工", "职员", "成员", "用户",
                "employee", "employees", "staff", "personnel", "user", "users", "member", "members");
        putAll(map, "列表", "名单", "清单", "名录", "list", "lists");
        putAll(map, "查询", "获取", "查看", "查找", "搜索", "检索", "列出", "显示", "展示",
                "query", "search", "find", "fetch");
        putAll(map, "待办", "todo", "task", "tasks", "pending", "backlog");
        putAll(map, "审批", "approval", "approvals", "approve");
        putAll(map, "我的", "my", "mine");
        putAll(map, "新增", "新建", "创建", "添加", "create", "add");
        putAll(map, "修改", "更新", "编辑", "update", "edit");
        putAll(map, "删除", "移除", "delete", "remove");
        putAll(map, "提交", "发起", "上报", "submit");
        return Collections.unmodifiableMap(map);
    }

    private static void putAll(Map<String, String> map, String canonical, String... aliases) {
        for (String alias : aliases) {
            map.put(alias, canonical);
        }
    }

    private static List<String> sortedNoise(String... words) {
        List<String> list = new ArrayList<>(List.of(words));
        list.sort(Comparator.comparingInt(String::length).reversed());
        return list;
    }
}
