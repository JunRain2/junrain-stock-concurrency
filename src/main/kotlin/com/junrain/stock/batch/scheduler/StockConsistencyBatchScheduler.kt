package com.junrain.stock.batch.scheduler

import com.junrain.stock.batch.job.StockConsistencyBatchJob
import jakarta.annotation.PostConstruct
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.stereotype.Component

@Component
class StockConsistencyBatchScheduler(
    private val scheduler: Scheduler
) {
    @PostConstruct
    fun registerJob() {
        val jobDetail = JobBuilder
            .newJob(StockConsistencyBatchJob::class.java)
            .storeDurably()
            .build()

        val trigger = TriggerBuilder.newTrigger()
            .forJob(jobDetail)
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(5)
                    .repeatForever()
            )
            .build()

        scheduler.scheduleJob(jobDetail, trigger)
    }
}