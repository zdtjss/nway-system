package com.nway.system.config;

import com.nway.system.common.security.LoginUser;
import com.nway.system.common.security.LoginUserContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * 异步任务与调度配置
 * <p>
 * 基于 Java 21 虚拟线程，高并发低开销，适合消息发送等 IO 密集场景。
 * 虚拟线程以信号量限制并发上限（背压），避免无限堆积抢占数据库连接池：
 * 超出上限时立即拒绝（fail-fast），由调用方感知，而不是静默排队直到连接耗尽。
 * <p>
 * <b>上下文传播</b>：所有异步执行器都挂 {@link #CONTEXT_PROPAGATING} 装饰器，把提交线程的
 * {@link LoginUser} 与 MDC(traceId) 复制到执行线程。否则 {@code @Async} 线程没有登录上下文，
 * 租户隔离策略会退化为"无上下文不隔离"（全域读），曾在 Webhook 回调路径上造成跨租户数据读取。
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 上下文传播装饰器：提交线程 → 执行线程复制 LoginUser 与 MDC(traceId)，执行完恢复/清理，
     * 避免虚拟线程复用导致上下文串号。
     */
    private static final TaskDecorator CONTEXT_PROPAGATING = runnable -> {
        LoginUser submitter = LoginUserContext.current();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            LoginUser previous = LoginUserContext.current();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            try {
                LoginUserContext.set(submitter);
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                runnable.run();
            } finally {
                if (previous != null) {
                    LoginUserContext.set(previous);
                } else {
                    LoginUserContext.clean();
                }
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
            }
        };
    };

    private static AsyncTaskExecutor decorated(ExecutorService delegate) {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(delegate);
        adapter.setTaskDecorator(CONTEXT_PROPAGATING);
        return adapter;
    }

    /**
     * {@code @Async} 未显式指定执行器名称时使用的默认执行器（同时把租户/追踪上下文传进异步线程）。
     */
    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        return decorated(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 消息发送异步执行器（带并发背压）
     */
    @Bean("messageTaskExecutor")
    public AsyncTaskExecutor messageTaskExecutor(
            @Value("${nway.async.message-max-concurrent:200}") int maxConcurrent) {
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore gate = new Semaphore(maxConcurrent);
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(command -> {
            if (!gate.tryAcquire()) {
                log.warn("消息发送并发已达上限({})，拒绝新任务，请关注下游通道与连接池容量", maxConcurrent);
                throw new RejectedExecutionException("消息发送并发已达上限：" + maxConcurrent);
            }
            virtualThreads.execute(() -> {
                try {
                    command.run();
                } finally {
                    gate.release();
                }
            });
        });
        adapter.setTaskDecorator(CONTEXT_PROPAGATING);
        return adapter;
    }
}