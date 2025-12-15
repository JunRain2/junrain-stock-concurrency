package com.example.demo.batch.job

import com.example.demo.global.logging.ErrorLogRepository
import com.example.demo.global.logging.ErrorLogType
import com.example.demo.product.command.domain.StockChange
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.*
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class StockConsistencyBatchJobConcurrencyTest {
    @Autowired
    private lateinit var scheduler: Scheduler

    @Autowired
    private lateinit var errorLogRepository: ErrorLogRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate


    private val executionLog = ConcurrentLinkedQueue<ExecutionEvent>()

    data class ExecutionEvent(
        val type: EventType,
        val timestamp: Long,
        val threadName: String,
        val jobKey: String
    )

    enum class EventType { START, END, VETOED }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM exception_logs")
        executionLog.clear()

        if (!scheduler.isStarted) {
            scheduler.start()
        }

        scheduler.getJobKeys(GroupMatcher.anyJobGroup()).forEach { jobKey ->
            scheduler.deleteJob(jobKey)
        }
    }

    @Test
    fun `@DisallowConcurrentExecution이 실제로 동시 실행을 방지해야 한다`() {
        // given
        createErrorLogs(count = 100, prefix = "concurrent-test")
        setLogCreationTime(minutesAgo = 2)

        val listenerName = "concurrency-test-listener"
        scheduler.listenerManager.addJobListener(createExecutionLogListener(listenerName))

        val jobDetail = createJobDetail("concurrency-test-job")

        // when: 거의 동시에 3번 트리거
        val triggers = (0..2).map { i ->
            createTrigger("trigger${i + 1}", jobDetail, delayMillis = i * 100L)
        }
        scheduler.scheduleJob(jobDetail, triggers.toSet(), true)

        Thread.sleep(15_000)

        // then
        val startEvents = executionLog.filter { it.type == EventType.START }
        val endEvents = executionLog.filter { it.type == EventType.END }
        val vetoedEvents = executionLog.filter { it.type == EventType.VETOED }

        assertTrue(
            startEvents.size + vetoedEvents.size >= 3,
            "3개의 트리거가 모두 처리되어야 함 (시작 또는 거부)"
        )

        assertEquals(
            startEvents.size,
            endEvents.size,
            "시작된 모든 작업은 완료되어야 함"
        )

        // 동시 실행이 없었는지 확인 (START와 END가 교차하지 않아야 함)
        if (startEvents.size >= 2) {
            for (i in 0 until startEvents.size - 1) {
                val currentEnd = endEvents[i].timestamp
                val nextStart = startEvents[i + 1].timestamp

                assertTrue(
                    currentEnd <= nextStart,
                    "작업 ${i + 1}이 완료된 후 작업 ${i + 2}가 시작되어야 함. " +
                            "작업 ${i + 1} 종료: $currentEnd, 작업 ${i + 2} 시작: $nextStart"
                )
            }
        }

        scheduler.listenerManager.removeJobListener(listenerName)
    }

    @Test
    fun `동시 실행 카운터로 최대 동시 실행 수가 1인지 검증`() {
        // given
        createErrorLogs(count = 50, prefix = "counter-test")
        setLogCreationTime(minutesAgo = 2)

        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val executionCount = AtomicInteger(0)

        val listenerName = "counter-listener"
        scheduler.listenerManager.addJobListener(
            createConcurrentCountListener(
                listenerName,
                currentConcurrent,
                maxConcurrent,
                executionCount
            )
        )

        val jobDetail = createJobDetail("counter-test-job")

        // when: 5개의 트리거를 짧은 간격으로 실행
        repeat(5) { i ->
            val trigger = createTrigger("counter-trigger-$i", jobDetail, delayMillis = i * 1000L)
            if (i == 0) {
                scheduler.scheduleJob(jobDetail, trigger)
            } else {
                scheduler.scheduleJob(trigger)
            }
        }

        Thread.sleep(20_000)

        // then
        assertEquals(
            1,
            maxConcurrent.get(),
            "@DisallowConcurrentExecution으로 인해 최대 동시 실행 수는 1이어야 함"
        )

        assertEquals(
            0,
            currentConcurrent.get(),
            "모든 작업이 완료되어 현재 실행 중인 작업은 0이어야 함"
        )

        assertTrue(
            executionCount.get() >= 1,
            "최소 1번 이상 실행되어야 함"
        )

        scheduler.listenerManager.removeJobListener(listenerName)
    }

    @Test
    fun `Job이 스케줄러에 등록되어 있는지 확인`() {
        // given
        val jobDetail = createJobDetail("registration-test-job")
        val trigger = createTrigger("registration-test-trigger", jobDetail)

        // when
        scheduler.scheduleJob(jobDetail, trigger)

        // then
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup())
        assertTrue(jobKeys.any { it.name == "registration-test-job" }, "Job이 스케줄러에 등록되어 있어야 함")

        val retrievedJob = scheduler.getJobDetail(jobDetail.key)
        assertNotNull(retrievedJob)
        assertEquals(
            StockConsistencyBatchJob::class.java,
            retrievedJob.jobClass,
            "등록된 Job의 클래스가 StockConsistencyBatchJob이어야 함"
        )
    }

    @Test
    fun `Job이 실행되고 에러 로그가 처리되는지 확인`() {
        // given
        val testRequestKey = "execution-test-key"
        errorLogRepository.saveErrorLog(
            requestKey = testRequestKey,
            reason = ErrorLogType.STOCK_CHANGE,
            content = listOf(StockChange(productId = 1L, quantity = 10L))
        )

        setLogCreationTime(minutesAgo = 2, requestKey = testRequestKey)

        val beforeLogs = errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )
        assertTrue(
            beforeLogs.any { it.requestKey == testRequestKey },
            "실행 전에는 에러 로그가 조회되어야 함"
        )

        val jobDetail = createJobDetail("execution-test-job")
        val trigger = createTrigger("execution-test-trigger", jobDetail)

        // when
        scheduler.scheduleJob(jobDetail, trigger)
        Thread.sleep(5_000)

        // then
        val afterLogs = errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        assertFalse(
            afterLogs.any { it.requestKey == testRequestKey },
            "실행 후에는 is_executed=true로 변경되어 조회되지 않아야 함"
        )
    }

    // Helper methods
    private fun createErrorLogs(count: Int, prefix: String) {
        repeat(count) { i ->
            errorLogRepository.saveErrorLog(
                requestKey = "$prefix-$i",
                reason = ErrorLogType.STOCK_CHANGE,
                content = listOf(StockChange(productId = i.toLong(), quantity = 10L))
            )
        }
    }

    private fun setLogCreationTime(minutesAgo: Int, requestKey: String? = null) {
        val sql = if (requestKey != null) {
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL $minutesAgo MINUTE) WHERE request_key = ?"
        } else {
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL $minutesAgo MINUTE)"
        }

        if (requestKey != null) {
            jdbcTemplate.update(sql, requestKey)
        } else {
            jdbcTemplate.update(sql)
        }
    }

    private fun createJobDetail(jobName: String): JobDetail {
        return JobBuilder.newJob(StockConsistencyBatchJob::class.java)
            .withIdentity(jobName, TEST_GROUP)
            .build()
    }

    private fun createTrigger(
        triggerName: String,
        jobDetail: JobDetail,
        delayMillis: Long = 0
    ): Trigger {
        return TriggerBuilder.newTrigger()
            .withIdentity(triggerName, TEST_GROUP)
            .forJob(jobDetail)
            .startAt(Date(System.currentTimeMillis() + delayMillis))
            .build()
    }

    private fun createExecutionLogListener(name: String): JobListener {
        return object : JobListener {
            override fun getName() = name

            override fun jobToBeExecuted(context: JobExecutionContext) {
                executionLog.add(createEvent(EventType.START, context))
            }

            override fun jobWasExecuted(
                context: JobExecutionContext,
                jobException: JobExecutionException?
            ) {
                executionLog.add(createEvent(EventType.END, context))
            }

            override fun jobExecutionVetoed(context: JobExecutionContext) {
                executionLog.add(createEvent(EventType.VETOED, context))
            }

            private fun createEvent(type: EventType, context: JobExecutionContext): ExecutionEvent {
                return ExecutionEvent(
                    type = type,
                    timestamp = System.currentTimeMillis(),
                    threadName = Thread.currentThread().name,
                    jobKey = context.jobDetail.key.toString()
                )
            }
        }
    }

    private fun createConcurrentCountListener(
        name: String,
        currentConcurrent: AtomicInteger,
        maxConcurrent: AtomicInteger,
        executionCount: AtomicInteger
    ): JobListener {
        return object : JobListener {
            override fun getName() = name

            override fun jobToBeExecuted(context: JobExecutionContext) {
                val current = currentConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                executionCount.incrementAndGet()
            }

            override fun jobWasExecuted(
                context: JobExecutionContext,
                jobException: JobExecutionException?
            ) {
                currentConcurrent.decrementAndGet()
            }

            override fun jobExecutionVetoed(context: JobExecutionContext) {}
        }
    }

    companion object {
        private const val TEST_GROUP = "test-group"
    }
}
