package com.nway.system.common.security;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份契约测试（编号 T1 / 契约 C-IDENT）。
 * <p>
 * nway-system 是宿主平台，也是身份源头（身份的唯一签发方）。本测试以共享契约资产
 * {@code contracts/host-user-token.json} 为单一事实源，校验宿主用户 Token 的声明基准：
 * <ol>
 *     <li>签发→解析往返：由身份源头签发的 Token 能被一致地解析出工号/租户/角色；</li>
 *     <li>声明名契约（tripwire）：必须存在的声明名；</li>
 *     <li>已知缺口断言（反向）：当前未签发、且不应出现的声明名。</li>
 * </ol>
 * 本测试为零行为变更：只新增测试，不改任何生产代码，不修改 pom，不影响其它测试。
 *
 * @author nway
 */
@DisplayName("身份契约 C-IDENT：宿主用户 Token 声明基准（三方共享）")
class HostUserTokenContractTest {

    /**
     * 测试签名密钥。
     * 取值需 ≥32 字节，以满足 {@link JwtUtil#getSecretKey(String)} 对 HMAC 密钥长度的要求
     * （jjwt 会按密钥长度自动选择 HS256/384/512）。
     */
    private static final String SECRET = "nway-system-identity-contract-test-secret-0123456789";

    /**
     * 契约 fixture 默认相对路径。
     * <p>
     * Maven 测试的工作目录为各模块根目录，nway-system-server 上溯三级即平台根，故为
     * {@code ../../../contracts/host-user-token.json}。
     */
    private static final String DEFAULT_FIXTURE_PATH = "../../../contracts/host-user-token.json";

    /** 契约 fixture：规范声明集合（与 {@code expected} 同源）。 */
    private static JSONObject fixture;

    /** 契约 expected：规范解析结果，取值以此为准，避免测试硬编码。 */
    private static JSONObject expected;

    @BeforeAll
    static void loadFixture() {
        String override = System.getProperty("contract.fixture");
        File file = (override != null && !override.isBlank())
                ? new File(override)
                : new File(DEFAULT_FIXTURE_PATH);

        if (!file.isFile()) {
            throw new IllegalStateException(
                    "未找到身份契约 fixture 文件: " + file.getAbsolutePath()
                            + "。请从模块目录运行 Maven 测试，或用 -Dcontract.fixture 指定路径。");
        }

        String json;
        try {
            json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "读取身份契约 fixture 失败: " + file.getAbsolutePath(), e);
        }

        fixture = JSON.parseObject(json);
        expected = fixture.getJSONObject("expected");
        assertThat(expected)
                .as("身份契约 fixture 必须包含 expected 段")
                .isNotNull();
    }

    @Test
    @DisplayName("签发→解析往返：身份源头签发的 Token 可被一致解析")
    void issuedTokenRoundTrip() {
        String token = issueFixtureToken();
        Claims claims = JwtUtil.parseToken(token, SECRET);

        assertThat(claims).as("按测试密钥解析应得到 Claims").isNotNull();
        assertThat(JwtUtil.getEmployeeId(claims)).isEqualTo(expected.getString("employeeId"));
        assertThat(JwtUtil.getTenantCode(claims)).isEqualTo(expected.getString("tenantCode"));
        assertThat(JwtUtil.getRoles(claims))
                .containsExactlyInAnyOrderElementsOf(expected.getList("roles", String.class));
        assertThat(JwtUtil.isSuperAdmin(claims)).isFalse();
        // 用户 Token 不得被误判为开放平台 Token（type != "open"）
        assertThat(JwtUtil.isOpenToken(claims)).isFalse();
    }

    @Test
    @DisplayName("声明名契约 tripwire：iss / aud / jti / sub / tenantCode / roles / superAdmin / exp 必须存在")
    void requiredClaimNamesPresent() {
        JSONObject payload = decodePayload(issueFixtureToken());
        assertThat(payload)
                .as("身份源头签发的 Token 必须包含这些声明名")
                .containsKeys("iss", "aud", "jti", "sub", "tenantCode", "roles", "superAdmin", "iat", "exp");
    }

    @Test
    @DisplayName("契约 v0.2：iss/aud/jti 取值正确，且不再签发宿主内部主键 uid")
    void issuerAudienceJtiPresentAndUidAbsent() {
        Claims claims = JwtUtil.parseToken(issueFixtureToken(), SECRET);
        assertThat(claims).isNotNull();
        assertThat(claims.getIssuer()).isEqualTo(JwtUtil.ISSUER);
        assertThat(claims.getAudience()).containsExactlyInAnyOrderElementsOf(JwtUtil.AUDIENCE);
        assertThat(claims.getId()).as("jti 必须存在，便于审计与重放排查").isNotBlank();

        // uid 已从契约移除：宿主主键是 VARCHAR 业务 id，与下游本地 BIGINT 用户 id 不同空间
        assertThat(decodePayload(issueFixtureToken()))
                .as("宿主用户 Token 不得携带内部主键 uid（契约 C-IDENT v0.2）")
                .doesNotContainKey("uid");
    }

    @Test
    @DisplayName("已知缺口断言：name / deptCode / employee_id / uid 当前未签发")
    void unsupportedClaimNamesAbsent() {
        JSONObject payload = decodePayload(issueFixtureToken());

        // 反向断言：以下声明名当前【不应】出现在 nway-system 签发的 Token 中。
        //  1) name / deptCode：nway-system 作为身份源头未签发姓名与部门编码，导致
        //     workflow-engine 侧 realName 只能退化为工号（契约差异 D1）。姓名/部门
        //     来自业务 DB 而非 Token，属尚未收敛的契约缺口。
        //  2) employee_id：nway-system 现状以 sub 承载工号，与 wform-core 签发工作流
        //     引擎 Token 时使用的 employee_id 命名不一致（决策 D3 未定）。这也是
        //     fixture 同时保留取值相同的 sub 与 employee_id 的原因。
        //
        // 若本断言将来失败，说明平台已开始签发这些声明 —— 此时应同步更新
        // contracts/host-user-token.json 与《集成契约.md》的 C-IDENT 一节。
        // 这正是本测试的价值：把"能跑但契约不存在"的隐性分歧显式暴露出来。
        assertThat(payload)
                .as("身份源头当前不应签发 name/deptCode/employee_id（契约差异 D1、决策 D3）/ uid（v0.2 移除）")
                .doesNotContainKeys("name", "deptCode", "employee_id", "uid");
    }

    /**
     * 按 fixture 的规范值签发一个宿主用户 Token（工号/租户/角色均取自 expected）。
     */
    private static String issueFixtureToken() {
        return JwtUtil.generateToken(
                expected.getString("employeeId"),
                expected.getString("tenantCode"),
                false,
                expected.getList("roles", String.class),
                SECRET);
    }

    /**
     * 将 JWT 的 payload 段用 Base64url 解码为 JSON 对象。
     */
    private static JSONObject decodePayload(String token) {
        String[] parts = token.split("\\.");
        assertThat(parts).as("JWT 应由 header.payload.signature 三段组成").hasSize(3);
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return JSON.parseObject(new String(decoded, StandardCharsets.UTF_8));
    }
}