package com.ticket.performance;

import com.ticket.dao.mongo.LogDAO;
import com.ticket.model.ActionLog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("stress")
class ActionLogStressTest {
    private static final int TOTAL_LOGS = 10_000;
    private static final int CONCURRENCY = 50;

    @Test
    @EnabledIfSystemProperty(named = "stress", matches = "true")
    void shouldWriteTenThousandActionLogsWithFiftyWorkers() throws Exception {
        LogDAO logDAO = new LogDAO();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger(0);
        List<Exception> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        long startedAt = System.nanoTime();

        for (int worker = 0; worker < CONCURRENCY; worker += 1) {
            executor.submit(() -> {
                try {
                    start.await();
                    int current;
                    while ((current = sequence.getAndIncrement()) < TOTAL_LOGS) {
                        logDAO.insert(buildLog(current));
                    }
                } catch (Exception ex) {
                    failures.add(ex);
                }
            });
        }

        start.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assertions.assertTrue(finished, "stress test timed out");
        Assertions.assertTrue(failures.isEmpty(), () -> "stress test failures: " + failures);
        System.out.println("Inserted " + TOTAL_LOGS + " action logs with " + CONCURRENCY
            + " workers in " + elapsedMillis + " ms");
    }

    private ActionLog buildLog(int index) {
        ActionLog log = new ActionLog();
        log.setUserId(String.valueOf(10001 + (index % 50)));
        log.setItemId(String.valueOf(2001 + (index % 200)));
        log.setActionType(index % 2 == 0 ? "VIEW" : "SEARCH");
        log.setDurationSeconds(String.valueOf(index % 180));
        ActionLog.ClientInfo clientInfo = new ActionLog.ClientInfo();
        clientInfo.setClientType("STRESS");
        clientInfo.setIp("127.0.0.1");
        log.setClientInfo(clientInfo);
        log.setCreatedAt(Instant.now());
        return log;
    }
}
