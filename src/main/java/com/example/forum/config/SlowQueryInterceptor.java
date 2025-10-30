package com.example.forum.config;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * MyBatis-Plus 慢查询监控拦截器
 *
 * 此拦截器功能：
 * - 记录超过阈值的 SQL 查询
 * - 记录查询执行指标
 * - 帮助识别性能瓶颈
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlowQueryInterceptor implements InnerInterceptor {

    private final MeterRegistry meterRegistry;

    // 慢查询阈值（毫秒）
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        // 在线程本地存储开始时间
        QueryTimingHolder.startTiming();
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {
        // 在线程本地存储开始时间
        QueryTimingHolder.startTiming();
    }

    /**
     * 查询执行后，检查是否为慢查询
     */
    private void afterQueryExecution(MappedStatement ms, BoundSql boundSql) {
        long duration = QueryTimingHolder.endTiming();

        // 记录指标
        Timer.builder("forum.database.query")
                .description("数据库查询执行时间")
                .tag("statement", ms.getId())
                .register(meterRegistry)
                .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 记录慢查询
        if (duration > SLOW_QUERY_THRESHOLD_MS) {
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            log.warn("检测到慢查询: 语句: {}, 耗时: {}ms, SQL: {}",
                    ms.getId(), duration, sql);

            // 记录慢查询指标
            meterRegistry.counter("forum.database.slow_queries",
                    "statement", ms.getId()).increment();
        } else {
            log.debug("查询已执行: 语句: {}, 耗时: {}ms", ms.getId(), duration);
        }
    }

    /**
     * 查询计时的线程本地持有者
     */
    private static class QueryTimingHolder {
        private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

        static void startTiming() {
            START_TIME.set(System.currentTimeMillis());
        }

        static long endTiming() {
            Long startTime = START_TIME.get();
            if (startTime == null) {
                return 0;
            }
            long duration = System.currentTimeMillis() - startTime;
            START_TIME.remove();
            return duration;
        }
    }
}