package com.example.data.scanner

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeletionPlan {
    data class Direct(val uris: List<String>) : DeletionPlan()
    data class RequiresIntentSender(val intentSenderRequest: IntentSenderRequest, val uris: List<String>) : DeletionPlan()
}

object MediaDeletionHelper {

    /**
     * Determines whether deletion can be performed directly or if Android 11+ Scoped Storage
     * requires launching a user confirmation dialog via IntentSenderRequest.
     */
    fun planDeletion(context: Context, uris: List<String>): DeletionPlan {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaStoreUris = uris
                .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                .filter { it.authority == MediaStore.AUTHORITY || it.toString().startsWith("content://media/") }

            if (mediaStoreUris.isNotEmpty()) {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    mediaStoreUris
                )
                return DeletionPlan.RequiresIntentSender(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    uris
                )
            }
        }
        return DeletionPlan.Direct(uris)
    }

    /**
     * Prepares an IntentSenderRequest on Android 11+ (API 30+) if the system requires
     * user confirmation dialog to delete MediaStore files.
     * Returns null if running on Android 10 or below, or if all items can be deleted directly.
     */
    fun createSystemDeleteRequest(context: Context, uris: List<String>): IntentSenderRequest? {
        val plan = planDeletion(context, uris)
        return (plan as? DeletionPlan.RequiresIntentSender)?.intentSenderRequest
    }

    /**
     * Deletes files directly via ContentResolver or File API.
     */
    suspend fun executeDirectDelete(contentResolver: ContentResolver, uris: List<String>) = withContext(Dispatchers.IO) {
        uris.forEach { uriString ->
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        file.delete()
                    }
                } else {
                    contentResolver.delete(uri, null, null)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Legacy helper method preserved for backwards compatibility.
     */
    suspend fun deleteDirectly(contentResolver: ContentResolver, uris: List<String>) {
        executeDirectDelete(contentResolver, uris)
    }
}
