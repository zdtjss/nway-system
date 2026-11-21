# nway-system-authz-sdk

对外部应用开放的授权接入 SDK：**一个依赖 + 两行配置 + 一个注解**获得

- 接口级授权（`@RequiresPermission` → 平台策略判定）
- 数据级授权（平台下发的数据谓词 → 参数化 SQL 片段）

> 本 SDK 面向外部应用，不参与 nway-system / nway-wform / workflow-engine 三工程互调。
> 判定语义与平台决策内核共用 `contracts/authz-cases.json` 契约用例。

## 1. 引入依赖

```xml
<dependency>
  <groupId>com.nway.system</groupId>
  <artifactId>nway-system-authz-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

## 2. 配置

```yaml
nway:
  authz:
    platform-url: http://localhost:8081/nway-system
    app-code: order-system
    app-secret: ${NWAY_APP_SECRET}
    # cache-ttl: 1m        # 策略包有效缓存
    # stale-ttl: 12h       # 平台不可用时沿用历史策略
    # fail-open: false     # 默认拒绝（安全默认）
```

## 3. 接口级授权

```java
@RestController
@RequestMapping("order")
@RequiresPermission(resource = "order", action = "read")
public class OrderController {

    @RequiresPermission(resource = "order", action = "write")
    @PostMapping
    public Order create(@RequestBody Order order) { ... }
}
```

主体解析（二选一）：

- 内置 `ThreadLocalSubjectResolver`：在认证过滤器里 `ThreadLocalSubjectResolver.set(employeeId)`，
  请求结束 `clear()`；
- 自定义 `SubjectResolver` Bean（推荐从你们现有 JWT 里取工号）。

## 4. 数据级授权

```java
AuthzBundle.DataPredicate predicate = client.predicates(employeeId, "order").get(0);
PredicateSqlBuilder.SqlFragment where = PredicateSqlBuilder.build(predicate,
        new PredicateSqlBuilder.FieldMapping("created_by", "dept_code"));
if (where != null) {
    sql.append(" AND ").append(where.sql());
    params.addAll(where.params());
}
```

谓词语义（由平台决策内核下发，SDK 只翻译）：

| kind | 含义 | SQL |
| --- | --- | --- |
| `ALL` | 不限制 | 不追加条件 |
| `SELF` | 仅本人 | `created_by = ?` |
| `DEPT` / `DEPT_LIST` | 部门集合 | `dept_code IN (?,...)` |
| `DEPT_TREE` | 部门及下级 | `dept_code LIKE ?` |
| `NONE` / 未知 | 无数据 | `1 = 0` |

## 5. 可靠性

- 策略包按**全局授权版本**缓存：平台返回新版本即替换；
- 平台不可用时沿用最后一次成功结果（`stale-ttl`），超期且无缓存时按 `fail-open`（默认拒绝）；
- 应用 Token 自动换取与刷新（`app-secret` 模式），401 自动重试一次；
- 字段名白名单校验 + 全参数化，杜绝 SQL 拼接。

## 6. 自测

```bash
mvn -pl nway-system-authz-sdk test
```

契约用例：`contracts/authz-cases.json`；策略包样例：`contracts/open-bundle.json`。
