package com.example.demo.batch.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.quartz.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component
import kotlin.test.Test

@SpringBootTest
class QuartzTest {

    @Autowired
    private lateinit var scheduler: Scheduler

    @Test
    fun `같은 Trigger 인스턴스를 두 번 등록하면 에러 발생`() {
        // given
        val job1 = JobBuilder.newJob(TestJob1::class.java)
            .withIdentity("job1")
            .build()

        val job2 = JobBuilder.newJob(TestJob2::class.java)
            .withIdentity("job2")
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .withIdentity("reuseTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(10)
                    .repeatForever()
            )
            .build()

        // when & then
        scheduler.scheduleJob(job1, trigger)  // 첫 번째 등록 성공

        assertThrows<ObjectAlreadyExistsException> {
            scheduler.scheduleJob(job2, trigger)  // 같은 identity로 재등록 시도
        }
    }

    @Test
    fun `Trigger에 JobKey가 설정되면 다른 Job과 함께 등록 시 에러`() {
        // given
        val job1 = JobBuilder.newJob(TestJob1::class.java)
            .withIdentity("job1")
            .build()

        val job2 = JobBuilder.newJob(TestJob2::class.java)
            .withIdentity("job2")
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .forJob(job1)  // job1과 연결
            .withIdentity("trigger1")
            .build()

        // when & then
        assertThrows<SchedulerException> {
            scheduler.scheduleJob(job2, trigger)  // job2와 등록 시도
        }
        // 에러 메시지: "Trigger does not reference given job!"
    }

    @Test
    fun `Trigger 등록 후 jobKey가 설정되는지 확인`() {
        // given
        val jobDetail = JobBuilder.newJob(TestJob1::class.java)
            .withIdentity("myJob")
            .build()

        val trigger = TriggerBuilder.newTrigger()
            // forJob 없음
            .withIdentity("myTrigger")
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInSeconds(10)
                    .repeatForever()
            )
            .build()

        // when
        assertThat(trigger.jobKey).isNull()  // 등록 전: null

        scheduler.scheduleJob(jobDetail, trigger)

        // then
        assertThat(trigger.jobKey).isNotNull()  // 등록 후: 설정됨
        assertThat(trigger.jobKey.name).isEqualTo("myJob")
    }

    @Test
    fun `같은 이름의 Trigger를 새 인스턴스로 만들어도 중복 에러`() {
        // given
        val job1 = JobBuilder.newJob(TestJob1::class.java)
            .withIdentity("job1")
            .build()

        val job2 = JobBuilder.newJob(TestJob2::class.java)
            .withIdentity("job2")
            .build()

        val trigger1 = TriggerBuilder.newTrigger()
            .withIdentity("sameName")
            .build()

        val trigger2 = TriggerBuilder.newTrigger()
            .withIdentity("sameName")  // 같은 이름
            .build()

        // when & then
        scheduler.scheduleJob(job1, trigger1)

        assertThrows<ObjectAlreadyExistsException> {
            scheduler.scheduleJob(job2, trigger2)  // 다른 인스턴스지만 같은 이름
        }
    }
}

@Component
class TestJob1 : QuartzJobBean() {
    override fun executeInternal(context: JobExecutionContext) {
        println("TestJob1 실행")
    }
}

@Component
class TestJob2 : QuartzJobBean() {
    override fun executeInternal(context: JobExecutionContext) {
        println("TestJob2 실행")
    }
}