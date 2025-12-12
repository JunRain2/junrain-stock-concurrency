package com.example.demo.batch.job

import com.example.demo.global.logging.ErrorLogRepository
import com.example.demo.global.logging.ErrorLogType
import com.example.demo.product.command.domain.StockChange
import com.example.demo.product.command.infrastructure.redis.RedisStockRepository
import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.*
import org.quartz.impl.matchers.GroupMatcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SpringBootTest
class StockConsistencyBatchJobConcurrencyTest {

    @TestConfiguration
    class QuartzTestConfig {
        @Bean
        @Primary
        fun testScheduler(
            errorLogRepository: ErrorLogRepository,
            redisStockRepository: RedisStockRepository
        ): Scheduler {
            val factory = SchedulerFactoryBean()

            // In-memory JobStore 사용
            val properties = Properties()
            properties["org.quartz.jobStore.class"] = "org.quartz.simpl.RAMJobStore"
            properties["org.quartz.threadPool.threadCount"] = "5"
            factory.setQuartzProperties(properties)

            // Job Factory 설정 - Spring Bean 주입을 위해
            val jobFactory = object : org.springframework.scheduling.quartz.SpringBeanJobFactory() {
                override fun createJobInstance(bundle: org.quartz.spi.TriggerFiredBundle): Any {
                    return StockConsistencyBatchJob(errorLogRepository, redisStockRepository)
                }
            }
            factory.setJobFactory(jobFactory)

            factory.setAutoStartup(false)
            factory.afterPropertiesSet()

            return factory.scheduler
        }
    }

    @Autowired
    private lateinit var scheduler: Scheduler

    @Autowired
    private lateinit var errorLogRepository: ErrorLogRepository

    @Autowired
    private lateinit var redisStockRepository: RedisStockRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val executionLog = ConcurrentLinkedQueue<ExecutionEvent>()
    private val lock = ReentrantLock()

    data class ExecutionEvent(
        val type: String, // "START", "END", "VETOED"
        val timestamp: Long,
        val threadName: String,
        val jobKey: String
    )

    @BeforeEach
    fun setUp() {
        // DB 초기화
        jdbcTemplate.execute("DELETE FROM exception_logs")
        executionLog.clear()

        // 스케줄러 시작
        if (!scheduler.isStarted) {
            scheduler.start()
        }

        // 기존 Job과 Trigger 정리
        scheduler.getJobKeys(GroupMatcher.anyJobGroup()).forEach { jobKey ->
            scheduler.deleteJob(jobKey)
        }
    }

    // ==================== 동시 실행 방지 검증 ====================

    @Test
    fun `@DisallowConcurrentExecution이 실제로 동시 실행을 방지해야 한다`() {
        // given: 실행 시간을 늘리기 위해 많은 에러 로그 생성
        repeat(100) { i ->
            errorLogRepository.saveErrorLog(
                requestKey = "concurrent-test-$i",
                reason = ErrorLogType.STOCK_CHANGE,
                content = listOf(StockChange(productId = i.toLong(), quantity = 10L))
            )
        }

        // 1분 이상 경과하도록 설정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE)"
        )

        // Job 실행 추적을 위한 리스너 등록
        val listenerName = "concurrency-test-listener"
        scheduler.listenerManager.addJobListener(object : JobListener {
            override fun getName() = listenerName

            override fun jobToBeExecuted(context: JobExecutionContext) {
                lock.withLock {
                    executionLog.add(
                        ExecutionEvent(
                            type = "START",
                            timestamp = System.currentTimeMillis(),
                            threadName = Thread.currentThread().name,
                            jobKey = context.jobDetail.key.toString()
                        )
                    )
                }
                println("[${Thread.currentThread().name}] Job 시작: ${context.jobDetail.key}")
            }

            override fun jobWasExecuted(
                context: JobExecutionContext,
                jobException: JobExecutionException?
            ) {
                lock.withLock {
                    executionLog.add(
                        ExecutionEvent(
                            type = "END",
                            timestamp = System.currentTimeMillis(),
                            threadName = Thread.currentThread().name,
                            jobKey = context.jobDetail.key.toString()
                        )
                    )
                }
                println("[${Thread.currentThread().name}] Job 완료: ${context.jobDetail.key}")
            }

            override fun jobExecutionVetoed(context: JobExecutionContext) {
                lock.withLock {
                    executionLog.add(
                        ExecutionEvent(
                            type = "VETOED",
                            timestamp = System.currentTimeMillis(),
                            threadName = Thread.currentThread().name,
                            jobKey = context.jobDetail.key.toString()
                        )
                    )
                }
                println("[${Thread.currentThread().name}] Job 실행 거부됨 (동시 실행 방지): ${context.jobDetail.key}")
            }
        })

        val jobDetail = JobBuilder.newJob(StockConsistencyBatchJob::class.java)
            .withIdentity("concurrency-test-job", "test-group")
            .build()

        // when: 거의 동시에 3번 트리거
        val trigger1 = TriggerBuilder.newTrigger()
            .withIdentity("trigger1", "test-group")
            .forJob(jobDetail)
            .startNow()
            .build()

        val trigger2 = TriggerBuilder.newTrigger()
            .withIdentity("trigger2", "test-group")
            .forJob(jobDetail)
            .startAt(Date(System.currentTimeMillis() + 100)) // 0.1초 후
            .build()

        val trigger3 = TriggerBuilder.newTrigger()
            .withIdentity("trigger3", "test-group")
            .forJob(jobDetail)
            .startAt(Date(System.currentTimeMillis() + 200)) // 0.2초 후
            .build()

        scheduler.scheduleJob(jobDetail, setOf(trigger1, trigger2, trigger3), true)

        // 충분히 대기 (배치 작업이 완료될 때까지)
        Thread.sleep(15000)

        // then: 실행 로그 분석
        println("\n=== Execution Log (총 ${executionLog.size}개 이벤트) ===")
        executionLog.forEach { println(it) }

        val startEvents = executionLog.filter { it.type == "START" }
        val endEvents = executionLog.filter { it.type == "END" }
        val vetoedEvents = executionLog.filter { it.type == "VETOED" }

        println("\n=== 통계 ===")
        println("START 이벤트: ${startEvents.size}개")
        println("END 이벤트: ${endEvents.size}개")
        println("VETOED 이벤트: ${vetoedEvents.size}개")

        // 검증 1: 모든 트리거가 처리되어야 함 (실행 또는 거부)
        assertTrue(
            startEvents.size + vetoedEvents.size >= 3,
            "3개의 트리거가 모두 처리되어야 함 (시작 또는 거부)"
        )

        // 검증 2: 시작된 작업은 모두 완료되어야 함
        assertEquals(
            startEvents.size,
            endEvents.size,
            "시작된 모든 작업은 완료되어야 함"
        )

        // 검증 3: 동시 실행이 없었는지 확인 (START와 END가 교차하지 않아야 함)
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

        println("\n✅ @DisallowConcurrentExecution이 정상적으로 동시 실행을 방지했습니다!")

        // 리스너 정리
        scheduler.listenerManager.removeJobListener(listenerName)
    }

    @Test
    fun `동시 실행 카운터로 최대 동시 실행 수가 1인지 검증`() {
        // given: 실행 시간을 늘리기 위해 많은 에러 로그 생성
        repeat(50) { i ->
            errorLogRepository.saveErrorLog(
                requestKey = "counter-test-$i",
                reason = ErrorLogType.STOCK_CHANGE,
                content = listOf(StockChange(productId = i.toLong(), quantity = 10L))
            )
        }

        // 1분 이상 경과하도록 설정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE)"
        )

        val currentConcurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val executionCount = AtomicInteger(0)

        val listenerName = "counter-listener"
        scheduler.listenerManager.addJobListener(object : JobListener {
            override fun getName() = listenerName

            override fun jobToBeExecuted(context: JobExecutionContext) {
                val current = currentConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, current) }
                executionCount.incrementAndGet()

                println("[${Thread.currentThread().name}] 실행 시작 - 현재 동시 실행 수: $current, 최대: ${maxConcurrent.get()}")
            }

            override fun jobWasExecuted(
                context: JobExecutionContext,
                jobException: JobExecutionException?
            ) {
                val current = currentConcurrent.decrementAndGet()
                println("[${Thread.currentThread().name}] 실행 종료 - 현재 동시 실행 수: $current")
            }

            override fun jobExecutionVetoed(context: JobExecutionContext) {
                println("[${Thread.currentThread().name}] 실행 거부됨 (동시 실행 방지)")
            }
        })

        val jobDetail = JobBuilder.newJob(StockConsistencyBatchJob::class.java)
            .withIdentity("counter-test-job", "test-group")
            .build()

        // when: 5개의 트리거를 짧은 간격으로 실행
        repeat(5) { i ->
            val trigger = TriggerBuilder.newTrigger()
                .withIdentity("counter-trigger-$i", "test-group")
                .forJob(jobDetail)
                .startAt(Date(System.currentTimeMillis() + i * 200L)) // 0.2초 간격
                .build()

            if (i == 0) {
                // 첫 번째만 jobDetail과 함께 등록
                scheduler.scheduleJob(jobDetail, trigger)
            } else {
                // 나머지는 trigger만 등록
                scheduler.scheduleJob(trigger)
            }
        }

        // 충분히 대기
        Thread.sleep(20000)

        // then
        println("\n=== 최종 통계 ===")
        println("총 실행 횟수: ${executionCount.get()}")
        println("최대 동시 실행 수: ${maxConcurrent.get()}")
        println("현재 실행 중: ${currentConcurrent.get()}")

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

        println("\n✅ 동시 실행 수 검증 성공! 최대 동시 실행 수는 1입니다.")

        // 리스너 정리
        scheduler.listenerManager.removeJobListener(listenerName)
    }

    @Test
    fun `Job이 스케줄러에 등록되어 있는지 확인`() {
        // given
        val jobDetail = JobBuilder.newJob(StockConsistencyBatchJob::class.java)
            .withIdentity("registration-test-job", "test-group")
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("registration-test-trigger", "test-group")
            .forJob(jobDetail)
            .startNow()
            .build()

        // when
        scheduler.scheduleJob(jobDetail, trigger)

        // then
        val jobKeys = scheduler.getJobKeys(GroupMatcher.anyJobGroup())
        val jobExists = jobKeys.any { it.name == "registration-test-job" }

        assertTrue(jobExists, "Job이 스케줄러에 등록되어 있어야 함")

        // 추가 검증: Job의 클래스 타입 확인
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
        // given: 처리 가능한 에러 로그 생성
        val testRequestKey = "execution-test-key"
        errorLogRepository.saveErrorLog(
            requestKey = testRequestKey,
            reason = ErrorLogType.STOCK_CHANGE,
            content = listOf(StockChange(productId = 1L, quantity = 10L))
        )

        // 1분 이상 경과하도록 설정
        jdbcTemplate.update(
            "UPDATE exception_logs SET created_at = DATE_SUB(NOW(), INTERVAL 2 MINUTE) WHERE request_key = ?",
            testRequestKey
        )

        // 실행 전 상태 확인
        val beforeLogs = errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )
        assertTrue(
            beforeLogs.any { it.requestKey == testRequestKey },
            "실행 전에는 에러 로그가 조회되어야 함"
        )

        val jobDetail = JobBuilder.newJob(StockConsistencyBatchJob::class.java)
            .withIdentity("execution-test-job", "test-group")
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("execution-test-trigger", "test-group")
            .forJob(jobDetail)
            .startNow()
            .build()

        // when
        scheduler.scheduleJob(jobDetail, trigger)

        // Job 실행 대기
        Thread.sleep(5000)

        // then: 에러 로그가 실행됨으로 표시되어 더 이상 조회되지 않아야 함
        val afterLogs = errorLogRepository.findAllErrorLog(
            reason = ErrorLogType.STOCK_CHANGE,
            typeRef = object : TypeReference<List<StockChange>>() {}
        )

        assertFalse(
            afterLogs.any { it.requestKey == testRequestKey },
            "실행 후에는 is_executed=true로 변경되어 조회되지 않아야 함"
        )

        println("✅ Job이 정상적으로 실행되고 에러 로그가 처리되었습니다!")
    }
}
