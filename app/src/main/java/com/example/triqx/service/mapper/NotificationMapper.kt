package com.example.triqx.service.mapper

import android.service.notification.StatusBarNotification
import com.example.triqx.data.local.ContactEntity

/**
 * Interface defining the contract for translating app-specific notifications
 * into a standardized ParsedNotification model.
 */
interface NotificationMapper {
    /**
     * Determines whether this mapper handles the specified application package.
     */
    fun canHandle(packageName: String): Boolean

    /**
     * Translates a raw StatusBarNotification into a structured ParsedNotification.
     */
    fun parse(
        sbn: StatusBarNotification,
        rawJson: String?,
        matchedContact: ContactEntity?
    ): ParsedNotification
}
