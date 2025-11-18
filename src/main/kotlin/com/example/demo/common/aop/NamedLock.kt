package com.example.demo.common.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedLock(
    val key: Array<String> = []
)
