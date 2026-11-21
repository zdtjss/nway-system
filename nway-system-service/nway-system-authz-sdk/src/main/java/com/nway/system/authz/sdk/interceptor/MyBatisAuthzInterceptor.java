package com.nway.system.authz.sdk.interceptor;

import com.nway.system.authz.sdk.client.AuthzBundleClient;
import com.nway.system.authz.sdk.client.SubjectResolver;
import com.nway.system.authz.sdk.filter.PredicateSqlBuilder;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Map;

/**
 * MyBatis 数据权限插件：按 Mapper 方法（mappedStatement id）绑定资源，
 * 将平台下发的数据谓词追加到 SQL。
 * <p>
 * 配置（{@code nway.authz.mapper-resources}）：
 * <pre>
 * nway:
 *   authz:
 *     mapper-resources:
 *       com.example.mapper.OrderMapper.selectPage: order
 * </pre>
 * 约束：被拦截的查询必须使用 WHERE（否则插件会补 {@code WHERE}）；
 * 谓词通过命名参数 {@code #{authzN}} 注入，SQL 仍为参数化。
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public class MyBatisAuthzInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MyBatisAuthzInterceptor.class);

    private final AuthzBundleClient client;
    private final SubjectResolver subjectResolver;
    private final Map<String, String> mapperResources;

    public MyBatisAuthzInterceptor(AuthzBundleClient client, SubjectResolver subjectResolver,
                                   Map<String, String> mapperResources) {
        this.client = client;
        this.subjectResolver = subjectResolver;
        this.mapperResources = mapperResources == null ? Map.of() : Map.copyOf(mapperResources);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject meta = SystemMetaObject.forObject(handler);
        MappedStatement statement = (MappedStatement) meta.getValue("delegate.mappedStatement");
        String resource = mapperResources.get(statement.getId());
        if (resource == null) {
            return invocation.proceed();
        }
        String employeeId = subjectResolver.resolveEmployeeId();
        if (employeeId == null || employeeId.isBlank()) {
            return invocation.proceed();
        }
        BoundSql boundSql = handler.getBoundSql();
        PredicateSqlBuilder.MyBatisWhere where = PredicateSqlBuilder.toMybatis(
                client.predicates(employeeId, resource),
                PredicateSqlBuilder.FieldMapping.DEFAULT, "authz");
        if (where == null) {
            return invocation.proceed();
        }
        where.params().forEach(boundSql::setAdditionalParameter);
        String original = boundSql.getSql();
        String joiner = original.toLowerCase().contains(" where ") ? " AND " : " WHERE ";
        SystemMetaObject.forObject(boundSql).setValue("sql", original + joiner + where.sql());
        log.debug("MyBatis 数据权限已注入：statement={}, resource={}, employee={}",
                statement.getId(), resource, employeeId);
        return invocation.proceed();
    }
}
