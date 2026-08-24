package com.junrain.stock.infra.common.jpa

import com.fasterxml.jackson.databind.ObjectMapper
import org.hibernate.cfg.AvailableSettings
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Configuration

/**
 * Hibernate가 JSON 컬럼을 다룰 때 쓰는 ObjectMapper를 Spring의 것으로 바꾼다.
 *
 * Hibernate 기본 ObjectMapper에는 KotlinModule이 없어 Kotlin data class를 역직렬화하지 못한다.
 */
@Configuration
class JsonMappingConfig(
    private val objectMapper: ObjectMapper,
) : HibernatePropertiesCustomizer {
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.JSON_FORMAT_MAPPER] = JacksonJsonFormatMapper(objectMapper)
    }
}
