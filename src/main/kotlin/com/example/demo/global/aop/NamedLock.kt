package com.example.demo.global.aop

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedLock(
    val key: Array<String> = []
)
