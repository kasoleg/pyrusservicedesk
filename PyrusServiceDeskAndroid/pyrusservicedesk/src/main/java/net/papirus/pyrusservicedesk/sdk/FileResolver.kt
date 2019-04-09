package net.papirus.pyrusservicedesk.sdk

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.support.v4.content.ContentResolverCompat
import net.papirus.pyrusservicedesk.sdk.data.intermediate.FileData
import net.papirus.pyrusservicedesk.sdk.data.intermediate.FileUploadRequestData

/**
 * Helper for working with files.
 *
 * NB:
 * Only file URIs with schema == "content" can be processed.
 * In other cases NULL is returned when [getUploadFileData] or [getFileData] is invoked.
 *
 * @param contentResolver content resolver instance provided by an application
 */
internal class FileResolver(private val contentResolver: ContentResolver) {

    /**
     * Provides data for making upload file request.
     * NULL may be returned if file form the specified [fileUri] was not found or [Uri.getScheme] != "content"
     */
    fun getUploadFileData(fileUri: Uri): FileUploadRequestData? {
        val cursor = ContentResolverCompat.query(
            contentResolver,
            fileUri,
            null,
            null,
            null,
            null,
            null)
        if (cursor == null || !cursor.moveToFirst())
            return null
        return FileUploadRequestData(
            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)),
            contentResolver.openInputStream(fileUri)
        )
    }

    /**
     * Provides file data for the UI purposes. See [FileData].
     * NULL may be returned when [fileUri] is null or [fileUri] has [Uri.getScheme] != "content"
     */
    fun getFileData(fileUri: Uri?): FileData? {
        if (fileUri == null)
            return null
        val cursor = ContentResolverCompat.query(
            contentResolver,
            fileUri,
            null,
            null,
            null,
            null,
            null)
        if (cursor == null || !cursor.moveToFirst())
            return null
        var size = cursor.getInt(cursor.getColumnIndex(OpenableColumns.SIZE))
        if (size == 0) {
            contentResolver.openInputStream(fileUri).use {
                it?.let { stream ->
                    size = stream.available()
                }
            }
        }
        return FileData(
            cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)),
            size,
            fileUri,
            true)
    }
}