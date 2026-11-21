package com.nway.system.config;

import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 {@link AsyncConfig} 的上下文传播：@Async 默认执行器必须把提交线程的
 * {@link LoginUser} 与 MDC(traceId) 带进异步线程，否则租户隔离会退化为"无上下文不隔离"（全域读）。
 */
class AsyncConfigTest {

    @AfterEach
    void cleanup() {
        LoginUserContext.clean();
        MDC.clear();
    }

    @Test
    void asyncExecutorPropagatesLoginUserAndTraceId() throws Exception {
        AsyncTaskExecutor executor = new AsyncConfig().getAsyncExecutor();

        LoginUser submitter = new LoginUser();
        submitter.setEmployeeId("emp-oa");
        submitter.setTenantCode("OA");
        LoginUserContext.set(submitter);
        MDC.put("traceId", "trace-xyz");

        CompletableFuture<String> seen = new CompletableFuture<>();
        executor.execute(() -> {
            LoginUser inWorker = LoginUserContext.current();
            seen.complete((inWorker == null ? "null" : inWorker.getTenantCode()) + "|" + MDC.get("traceId"));
        });

        assertEquals("OA|trace-xyz", seen.get(5, TimeUnit.SECONDS),
                "异步线程应继承提交线程的租户上下文与 traceId");
    }
}