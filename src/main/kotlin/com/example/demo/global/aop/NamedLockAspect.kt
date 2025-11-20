package com.example.demo.global.aop

import com.example.demo.global.lock.LockRepository
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
class NamedLockAspect(
    private val lockRepository: LockRepository
) {
    @Around("@annotation(namedLock)")
    fun lock(joinPoint: ProceedingJoinPoint, namedLock: NamedLock): Any? {
        val signature = joinPoint.signature as MethodSignature
        val parameterNames = signature.parameterNames
        val args = joinPoint.args

        val lockKey = generateLockKey(namedLock.key, parameterNames, args)

        return lockRepository.executeWithLock(lockKey) {
            joinPoint.proceed()
        }
    }

    private fun generateLockKey(
        keyExpressions: Array<String>,
        parameterNames: Array<String>,
        args: Array<Any?>
    ): String {
        val context = StandardEvaluationContext().apply {
            parameterNames.forEachIndexed { index, name ->
                setVariable(name, args[index])
            }
        }

        return keyExpressions.joinToString(":") { keyExpression ->
            PARSER.parseExpression(keyExpression).getValue(context)?.toString().orEmpty()
        }
    }

    companion object {
        private val PARSER = SpelExpressionParser()
    }
}