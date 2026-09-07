package com.example.triqx.service.mapper

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry holding all app-specific notification mappers.
 * Evaluates in order of specificity and falls back to DefaultNotificationMapper.
 */
@Singleton
class NotificationMapperRegistry @Inject constructor() {

    private val mappers: List<NotificationMapper> = listOf(
        WhatsAppNotificationMapper(),
        TeamsNotificationMapper(),
        SlackNotificationMapper(),
        TelegramNotificationMapper(),
        EmailNotificationMapper(),
        DefaultNotificationMapper() // Fallback always last
    )

    /**
     * Resolves the appropriate mapper for the given application package.
     */
    fun getMapper(packageName: String): NotificationMapper {
        return mappers.first { it.canHandle(packageName) }
    }
}
