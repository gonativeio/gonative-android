package io.gonative.android.files

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class CapturedImageSaver {
    fun saveCapturedBitmap(context: Context, bitmapUri: Uri): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "IMG_$timeStamp.jpg"

        val resolver: ContentResolver = context.contentResolver
        val currentUri = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES)
            val captureFile = File(storageDir, imageFileName)
            FileProvider.getUriForFile(context, context.applicationContext.packageName + ".fileprovider", captureFile);
        }

        currentUri?.let {
            context.contentResolver.openOutputStream(it).use { output ->
                context.contentResolver.openInputStream(bitmapUri).use { input ->
                    output?.write(input?.readBytes())
                }
            }
        }

        return currentUri
    }
}