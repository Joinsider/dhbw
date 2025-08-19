/*
 * SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package de.fampopprol.dhbwhorb.data.dualis.network

import android.util.Log
import de.fampopprol.dhbwhorb.data.dualis.models.NotificationList
import de.fampopprol.dhbwhorb.data.dualis.models.Notification
import de.fampopprol.dhbwhorb.data.dualis.models.NotificationType
import de.fampopprol.dhbwhorb.data.demo.DemoDataProvider

/**
 * Handles notification operations for Dualis
 */
class DualisNotificationsService(
    private val networkClient: DualisNetworkClient,
    private val urlManager: DualisUrlManager,
    private val htmlParser: DualisHtmlParser,
    private val authService: DualisAuthenticationService
) {

    /**
     * Fetches unread notifications from Dualis
     */
    fun getUnreadNotifications(callback: (NotificationList?) -> Unit) {
        Log.d("DualisNotificationsService", "=== FETCHING UNREAD NOTIFICATIONS ===")

        // Return demo data if in demo mode
        if (authService.isDemoMode) {
            Log.d("DualisNotificationsService", "Demo mode: returning demo notifications")
            callback(createDemoNotifications())
            return
        }

        // Check authentication status
        if (!urlManager.hasValidToken()) {
            Log.e("DualisNotificationsService", "Auth Token is null. Authentication required.")
            callback(null)
            return
        }

        // For now, we'll construct the notification URL from the auth token
        // In a real implementation, you might need to extract the actual notification URL
        // from the main page navigation links
        val notificationsUrl = buildNotificationsUrl()
        if (notificationsUrl == null) {
            Log.e("DualisNotificationsService", "Could not build notifications URL")
            callback(null)
            return
        }

        Log.d("DualisNotificationsService", "Fetching notifications from: $notificationsUrl")

        val request = networkClient.createGetRequest(notificationsUrl)
        networkClient.makeRequest(request, "Notifications") { _, responseBody ->
            if (responseBody != null) {
                // Check if the response indicates an invalid token
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    Log.w("DualisNotificationsService", "Token appears to be invalid when fetching notifications, attempting re-authentication")
                    authService.reAuthenticateIfNeeded { success ->
                        if (success) {
                            Log.d("DualisNotificationsService", "Re-authentication successful, retrying notifications fetch")
                            getUnreadNotifications(callback) // Retry after re-authentication
                        } else {
                            Log.e("DualisNotificationsService", "Re-authentication failed")
                            callback(null)
                        }
                    }
                    return@makeRequest
                }

                try {
                    Log.d("DualisNotificationsService", "=== STARTING NOTIFICATIONS PARSING ===")
                    val notificationList = htmlParser.parseNotifications(responseBody)

                    Log.d("DualisNotificationsService", "=== NOTIFICATIONS PARSING SUCCESSFUL ===")
                    Log.d("DualisNotificationsService", "Total unread notifications: ${notificationList.totalUnreadCount}")
                    Log.d("DualisNotificationsService", "Schedule change notifications: ${notificationList.unreadNotifications.count { it.type == NotificationType.SCHEDULE_CHANGE }}")
                    Log.d("DualisNotificationsService", "Schedule set notifications: ${notificationList.unreadNotifications.count { it.type == NotificationType.SCHEDULE_SET }}")
                    Log.d("DualisNotificationsService", "General notifications: ${notificationList.unreadNotifications.count { it.type == NotificationType.GENERAL_MESSAGE }}")

                    // Log individual notifications for debugging
                    notificationList.unreadNotifications.forEach { notification ->
                        Log.d("DualisNotificationsService", "  - ${notification.date} ${notification.time}: ${notification.subject} (${notification.type})")
                    }

                    callback(notificationList)
                } catch (e: Exception) {
                    Log.e("DualisNotificationsService", "=== NOTIFICATIONS PARSING FAILED ===", e)
                    callback(null)
                }
            } else {
                Log.e("DualisNotificationsService", "Response body is null")
                callback(null)
            }
        }
    }

    /**
     * Fetches a specific notification's details
     */
    fun getNotificationDetails(notification: Notification, callback: (String?) -> Unit) {
        if (authService.isDemoMode) {
            // Return demo notification content
            callback(createDemoNotificationContent(notification))
            return
        }

        val detailUrl = notification.detailUrl
        if (detailUrl == null) {
            Log.w("DualisNotificationsService", "No detail URL available for notification ${notification.id}")
            callback(null)
            return
        }

        Log.d("DualisNotificationsService", "Fetching notification details from: $detailUrl")

        val request = networkClient.createGetRequest(detailUrl)
        networkClient.makeRequest(request, "Notification Details") { _, responseBody ->
            if (responseBody != null) {
                // Check if the response indicates an invalid token
                if (htmlParser.isTokenInvalidResponse(responseBody)) {
                    Log.w("DualisNotificationsService", "Token appears to be invalid when fetching notification details, attempting re-authentication")
                    authService.reAuthenticateIfNeeded { success ->
                        if (success) {
                            Log.d("DualisNotificationsService", "Re-authentication successful, retrying notification details fetch")
                            getNotificationDetails(notification, callback) // Retry after re-authentication
                        } else {
                            Log.e("DualisNotificationsService", "Re-authentication failed")
                            callback(null)
                        }
                    }
                    return@makeRequest
                }

                // For now, return the raw HTML content
                // You could add additional parsing here to extract specific content
                callback(responseBody)
            } else {
                Log.e("DualisNotificationsService", "Response body is null for notification details")
                callback(null)
            }
        }
    }

    /**
     * Builds the notifications URL using the auth token
     * Note: This is a simplified implementation. In practice, you might need to
     * extract the actual notification URL from the main page navigation.
     */
    private fun buildNotificationsUrl(): String? {
        val authToken = urlManager.getAuthToken()
        return if (authToken != null) {
            // Based on the example URL you provided, construct the notifications URL
            // The URL pattern seems to be the ACTION endpoint with a specific ARGUMENTS parameter
            // For now, we'll use a simplified approach - you may need to adjust this based on
            // how Dualis actually constructs the notification URLs
            "https://dualis.dhbw.de/scripts/mgrqispi.dll?APPNAME=CampusNet&PRGNAME=ACTION&ARGUMENTS=-N$authToken"
        } else {
            null
        }
    }

    /**
     * Creates demo notification data for testing
     */
    private fun createDemoNotifications(): NotificationList {
        val demoNotifications = listOf(
            Notification(
                id = "demo_1",
                date = "30.07.2025",
                time = "08:05",
                sender = "T4INF2904.2/C# und .NET",
                subject = "\"T4INF2904.2 / C# und .NET HOR-TINF2024\": Termin geändert",
                type = NotificationType.SCHEDULE_CHANGE,
                isUnread = true,
                detailUrl = null,
                deleteUrl = null
            ),
            Notification(
                id = "demo_2",
                date = "04.07.2025",
                time = "09:24",
                sender = "T4INF1102.1/Anwendungsprojekt In",
                subject = "\"T4INF1102.1 / Anwendungsprojekt Informatik HOR-TINF2024\": Termin geändert",
                type = NotificationType.SCHEDULE_CHANGE,
                isUnread = true,
                detailUrl = null,
                deleteUrl = null
            ),
            Notification(
                id = "demo_3",
                date = "04.07.2025",
                time = "09:17",
                sender = "T4_1000.2/Wissenschaft.Arbeit",
                subject = "\"T4_1000.2 / Wissenschaftliches Arbeiten 1 HOR-TINF2024\": Termin festgelegt",
                type = NotificationType.SCHEDULE_SET,
                isUnread = true,
                detailUrl = null,
                deleteUrl = null
            ),
            Notification(
                id = "demo_4",
                date = "14.11.2024",
                time = "11:31",
                sender = "T4INF1101.2/Web Engineering 2",
                subject = "\"T4INF1101.2 / Web Engineering 2 HOR-TINF2024\": Termin festgelegt",
                type = NotificationType.SCHEDULE_SET,
                isUnread = true,
                detailUrl = null,
                deleteUrl = null
            )
        )

        return NotificationList(demoNotifications, demoNotifications.size)
    }

    /**
     * Creates demo notification content for testing
     */
    private fun createDemoNotificationContent(notification: Notification): String {
        return """
            <html>
            <body>
                <h1>Notification Details</h1>
                <h2>${notification.subject}</h2>
                <p><strong>From:</strong> ${notification.sender}</p>
                <p><strong>Date:</strong> ${notification.date} at ${notification.time}</p>
                <p><strong>Type:</strong> ${notification.type}</p>
                <hr>
                <p>This is demo content for the notification. In a real implementation, 
                this would contain the actual notification details from Dualis.</p>
                <p>Schedule changes and course announcements would appear here with 
                specific details about what has been modified or announced.</p>
            </body>
            </html>
        """.trimIndent()
    }
}
