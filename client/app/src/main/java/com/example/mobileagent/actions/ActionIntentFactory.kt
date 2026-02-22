package com.example.mobileagent.actions

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.OffsetDateTime

object ActionIntentFactory {
    fun createDialIntent(number: String): Intent {
        return Intent(Intent.ACTION_DIAL).setData(Uri.parse("tel:$number"))
    }

    fun createSendSmsIntent(to: String, body: String?): Intent {
        val intent = Intent(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:$to"))
        if (!body.isNullOrBlank()) {
            intent.putExtra("sms_body", body)
        }
        return intent
    }

    fun createCalendarInsertIntent(title: String?, startAt: String?, endAt: String?): Intent {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        if (!title.isNullOrBlank()) {
            intent.putExtra(CalendarContract.Events.TITLE, title)
        }

        val startMillis = parseIsoToMillis(startAt)
        val endMillis = parseIsoToMillis(endAt)
        if (startMillis != null) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        }
        if (endMillis != null) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
        return intent
    }

    private fun parseIsoToMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            when {
                value.endsWith("Z", ignoreCase = true) -> Instant.parse(value).toEpochMilli()
                else -> OffsetDateTime.parse(value).toInstant().toEpochMilli()
            }
        }.getOrNull()
    }
}
