package com.nway.system.ai.service;

import com.nway.system.ai.domain.AiOutputField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("数据体量测量：行数/字符数/总数与展示裁剪")
class AiDataSizeTest {

    private final AiDataSize dataSize = new AiDataSize();

    private final AiLimits limits = new AiLimits();

    @Test
    @DisplayName("分页结构：按 records 计算行数，读取 total")
    void measuresPagedData() {
        Map<String, Object> data = Map.of(
                "records", List.of(Map.of("id", 1), Map.of("id", 2)),
                "total", 10);

        AiDataSize.Size size = dataSize.measure(data);

        assertThat(size.rows()).isEqualTo(2);
        assertThat(size.total()).isEqualTo(10L);
        assertThat(size.chars()).isGreaterThan(0);
    }

    @Test
    @DisplayName("code/msg/data 信封：先脱信封再测量")
    void unwrapsEnvelope() {
        Map<String, Object> data = Map.of(
                "code", 200,
                "message", "ok",
                "data", Map.of("list", List.of(Map.of("id", 1)), "totalCount", 3));

        AiDataSize.Size size = dataSize.measure(data);

        assertThat(size.rows()).isEqualTo(1);
        assertThat(size.total()).isEqualTo(3L);
    }

    @Test
    @DisplayName("outputSchema 声明数组字段时优先按声明取行")
    void prefersSchemaArrayField() {
        AiOutputField field = new AiOutputField();
        field.setKey("items");
        field.setType("ARRAY");
        Map<String, Object> data = Map.of(
                "items", List.of(Map.of("id", 1), Map.of("id", 2), Map.of("id", 3)),
                "records", List.of(Map.of("id", 9)));

        List<Object> rows = dataSize.extractRows(data, List.of(field));

        assertThat(rows).hasSize(3);
    }

    @Test
    @DisplayName("展示裁剪：只保留前 N 行")
    void trimsRowsForDisplay() {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("records", List.of(
                Map.of("id", 1), Map.of("id", 2), Map.of("id", 3), Map.of("id", 4)));

        Object trimmed = dataSize.trimForDisplay(data, null, 2);

        assertThat(dataSize.extractRows(trimmed, null)).hasSize(2);
    }

    @Test
    @DisplayName("分析上限：行数或 total 超限均判定超限")
    void checksAnalysisLimits() {
        AiDataSize.Size manyRows = new AiDataSize.Size(
                limits.getAnalysisMaxRows() + 1, 100, 100, null, false);
        AiDataSize.Size manyTotal = new AiDataSize.Size(1, 100, 100,
                (long) limits.getAnalysisMaxTotal() + 1, false);
        AiDataSize.Size small = new AiDataSize.Size(1, 100, 100, 1L, false);

        assertThat(dataSize.exceedsAnalysis(manyRows, limits)).isTrue();
        assertThat(dataSize.exceedsAnalysis(manyTotal, limits)).isTrue();
        assertThat(dataSize.exceedsAnalysis(small, limits)).isFalse();
    }

    @Test
    @DisplayName("hasNext：显式标记或 currentPage < totalPages")
    void detectsNextPage() {
        assertThat(dataSize.hasNext(Map.of("hasNext", true))).isTrue();
        assertThat(dataSize.hasNext(Map.of("currentPage", 1, "totalPages", 3))).isTrue();
        assertThat(dataSize.hasNext(Map.of("currentPage", 3, "totalPages", 3))).isFalse();
    }
}
