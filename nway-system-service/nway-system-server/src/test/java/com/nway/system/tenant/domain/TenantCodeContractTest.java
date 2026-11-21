package com.nway.system.tenant.domain;

import com.nway.system.common.security.JwtUtil;
import com.nway.system.common.tenant.TenantUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户编码契约测试（契约 {@code C-TENANT}，决策 P2-4 路线 A：大小写敏感、原样传递）。
 * <p>
 * 校验 nway-system（唯一事实源）在身份边界上原样承载编码（不做大小写归一化），
 * 且内置编码已登记在契约中。
 */
@DisplayName("租户编码契约 C-TENANT：平台原样承载编码")
class TenantCodeContractTest {

    /** Maven 测试工作目录为模块根 nway-system-server，上溯三级即平台根。 */
    private static final String DEFAULT_FIXTURE_PATH = "../../../contracts/host-tenant.json";
    private static final String CODE_FIXTURE_PATH = "../../../contracts/tenant-code.json";
    private static final String SECRET = "nway-system-jwt-secret-key-2026-please-change-in-production";

    private JSONObject contract() {
        String path = System.getProperty("contract.fixture", DEFAULT_FIXTURE_PATH);
        try {
            return JSON.parseObject(Files.readString(Path.of(path)));
        } catch (Exception e) {
            throw new IllegalStateException("读取租户契约失败: " + path, e);
        }
    }

    @Test
    @DisplayName("大小写原样经 JWT 承载；契约声明大小写敏感且不归一化")
    void tenantCodePreservedThroughJwt() {
        JSONObject c = contract();
        JSONObject rules = c.getJSONObject("rules");
        assertThat(rules.getBooleanValue("caseSensitive")).isTrue();
        assertThat(rules.getString("normalization")).isEqualTo("none");

        // 大写编码（如 OA）经签发-解析后必须原样保留
        String token = JwtUtil.generateToken("E10001", "OA", false, List.of("r_admin"), SECRET);
        Claims claims = JwtUtil.parseToken(token, SECRET);
        assertThat(JwtUtil.getTenantCode(claims)).isEqualTo("OA");

        List<String> existing = c.getJSONArray("existingCodes").toJavaList(String.class);
        assertThat(existing).contains(TenantUtil.PUBLIC_TENANT_CODE, TenantUtil.DEFAULT_TENANT_CODE);
    }

    @Test
    @DisplayName("编码规则契约：tenant-code.json 与 host-tenant.json 一致，正反例可通过 TenantCodePolicy")
    void tenantCodeRulesMatch() throws Exception {
        JSONObject hostRules = contract().getJSONObject("rules");
        JSONObject codeContract = JSON.parseObject(Files.readString(Path.of(CODE_FIXTURE_PATH)));
        JSONObject rules = codeContract.getJSONObject("rules");

        assertThat(rules.getString("pattern"))
                .isEqualTo(hostRules.getString("pattern"))
                .isEqualTo(TenantCodePolicy.PATTERN_TEXT);
        assertThat(rules.getIntValue("maxLength"))
                .isEqualTo(hostRules.getIntValue("maxLength"))
                .isEqualTo(TenantCodePolicy.MAX_LENGTH);
        assertThat(rules.getIntValue("minLength")).isEqualTo(hostRules.getIntValue("minLength"));
        assertThat(rules.getBooleanValue("caseSensitive")).isTrue();

        for (String valid : codeContract.getJSONArray("validExamples").toJavaList(String.class)) {
            assertThat(TenantCodePolicy.isValid(valid)).as("应接受：" + valid).isTrue();
        }
        for (Object node : codeContract.getJSONArray("invalidExamples")) {
            String invalid = ((JSONObject) node).getString("code");
            assertThat(TenantCodePolicy.isValid(invalid)).as("应拒绝：" + invalid).isFalse();
        }
    }
}