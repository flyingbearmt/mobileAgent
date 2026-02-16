package com.example.mobileagent.task

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.mobileagent.agent.AgentApi
import com.example.mobileagent.notify.TaskNotification
import kotlinx.coroutines.delay

class TaskPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val baseUrl = inputData.getString(KEY_BASE_URL) ?: return Result.failure()

        val api = AgentApi(baseUrl)

        repeat(15) {
            val resp = runCatching { api.getTask(taskId) }.getOrElse { err ->
                val msg = err.message ?: "poll failed"
                saveResult(taskId, msg)
                TaskNotification.notifyFailed(applicationContext, taskId, msg)
                return Result.success()
            }
            if (resp.status == "SUCCEEDED") {
                saveResult(taskId, resp.resultText.orEmpty())
                TaskNotification.notifyDone(applicationContext, taskId, resp.resultText.orEmpty())
                return Result.success()
            }
            if (resp.status == "FAILED") {
                val msg = resp.errorMessage ?: "failed"
                saveResult(taskId, msg)
                TaskNotification.notifyFailed(applicationContext, taskId, msg)
                return Result.success()
            }
            delay(2000)
        }

        val msg = "timeout"
        saveResult(taskId, msg)
        TaskNotification.notifyFailed(applicationContext, taskId, msg)
        return Result.success()
    }

    private fun saveResult(taskId: String, text: String) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("result_$taskId", text).apply()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_BASE_URL = "base_url"
        const val PREFS_NAME = "mobileagent"

        fun inputData(taskId: String, baseUrl: String): Data {
            return Data.Builder()
                .putString(KEY_TASK_ID, taskId)
                .putString(KEY_BASE_URL, baseUrl)
                .build()
        }
    }
}
