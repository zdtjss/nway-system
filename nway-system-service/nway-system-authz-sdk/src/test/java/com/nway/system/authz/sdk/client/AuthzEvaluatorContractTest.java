package com.nway.system.authz.sdk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nway.system.authz.sdk.model.AuthzBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：SDK 判定语义必须与 contracts/authz-cases.json 一致。
 * <p>
 * 与平台决策内核共用同一批用例，防止两端语义漂移。
 */
class AuthzEvaluatorContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("contracts/authz-cases.json：全部用例通过")
    void allContractCasesPass() throws Exception {
        File fixture = new File("../../../contracts/authz-cases.json");
        assertThat(fixture).as("契约用例文件应存在：" + fixture.getAbsolutePath()).exists();
        JsonNode root = MAPPER.readTree(fixture);

        int executed = 0;
        for (JsonNode node : root.path("cases")) {
            AuthzBundle bundle = MAPPER.convertValue(node.path("bundle"), AuthzBundle.class);
            boolean actual = AuthzEvaluator.allows(bundle, node.path("resourceCode").asText(),
                    node.path("action").asText());
            assertThat(actual)
                    .as("用例: " + node.path("name").asText())
                    .isEqualTo(node.path("expect").asBoolean());
            executed++;
        }
        assertThat(executed).isGreaterThan(0);
    }
}
