package com.example.demo.batch.scheduler

import com.example.demo.batch.job.StockConsistencyBatchJob
import jakarta.annotation.PostConstruct
import org.quartz.*
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


        scheduler.scheduleJob(jobDetail ,trigger)
    }
}