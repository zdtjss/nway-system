package com.example.authz;

import com.nway.system.authz.sdk.annotation.RequiresPermission;
import com.nway.system.authz.sdk.client.AuthzBundleClient;
import com.nway.system.authz.sdk.client.SubjectResolver;
import com.nway.system.authz.sdk.filter.PredicateSqlBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 示例：接口级授权 + 数据级谓词翻译。
 * <p>
 * 真实项目里 {@link SubjectResolver} 从你们自己的 JWT/会话取工号。
 */
@RestController
@RequestMapping("order")
@RequiresPermission(resource = "order", action = "read")
public class OrderController {

    private final AuthzBundleClient client;
    private final SubjectResolver subjectResolver;

    public OrderController(AuthzBundleClient client, SubjectResolver subjectResolver) {
        this.client = client;
        this.subjectResolver = subjectResolver;
    }

    @GetMapping
    public List<String> list() {
        String employeeId = subjectResolver.resolveEmployeeId();
        // 数据级：平台下发的谓词 → 参数化 SQL 片段
        PredicateSqlBuilder.SqlFragment where = PredicateSqlBuilder.buildAny(
                client.predicates(employeeId, "order"),
                new PredicateSqlBuilder.FieldMapping("created_by", "dept_code"));
        // where == null 表示不限制；非空时把 where.sql()/where.params() 拼到查询上
        return List.of("where=" + (where == null ? "ALL" : where.sql()));
    }
}
