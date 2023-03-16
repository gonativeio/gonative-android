package io.gonative.android

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.gonative.android.library.AppConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class FileUploadIntentsCreator(val context: Context, val mimeTypeSpecs: Array<String>, val multiple: Boolean) {
    private val mimeTypes = hashSetOf<String>()
    private val appConfig = AppConfig.getInstance(context)
    private var packageManger = context.packageManager

    var currentCaptureUri: Uri? = null

    init {
        extractMimeTypes()
    }

    private fun extractMimeTypes() {
        mimeTypeSpecs.forEach { spec ->
            val specParts = spec.split("[,;\\s]")
            specParts.forEach {
                if (it.startsWith(".")) {
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.substring(1))
                    mimeType?.let { it1 -> mimeTypes.add(it1) }
                } else if (it.contains("/")) {
                    mimeTypes.add(it)
                }
            }
        }

        if (mimeTypes.isEmpty()) {
            mimeTypes.add("*/*")
        }
    }

    private fun imagesAllowed(): Boolean {
        return mimeTypes.contains("*/*") || mimeTypes.any { it.contains("image/") }
    }

    private fun videosAllowed(): Boolean {
        return mimeTypes.contains("*/*") || mimeTypes.any { it.contains("video/") }
    }

    private fun photoCameraIntents(): ArrayList<Intent> {
        val intents = arrayListOf<Intent>()

        if (!appConfig.directCameraUploads) {
            return intents
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "IMG_$timeStamp.jpg"

        currentCaptureUri = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val resolver: ContentResolver = context.contentResolver
            val contentValues = ContentValues()
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/*")
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES)
            val captureFile = File(storageDir, imageFileName)
            Uri.fromFile(captureFile)
        }

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolveList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManger.queryIntentActivities(captureIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            packageManger.queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (resolve in resolveList) {
            val packageName = resolve.activityInfo.packageName
            val intent = Intent(captureIntent)
            intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
            intent.setPackage(packageName)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, currentCaptureUri)
            intents.add(intent)
        }

        return intents
    }

    private fun videoCameraIntents(): ArrayList<Intent> {
        val intents = arrayListOf<Intent>()

        if (!appConfig.directCameraUploads) {
            return intents
        }

        val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val resolveList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManger.queryIntentActivities(captureIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            packageManger.queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (resolve in resolveList) {
            val packageName = resolve.activityInfo.packageName
            val intent = Intent(captureIntent)
            intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
            intent.setPackage(packageName)
            intents.add(intent)
        }

        return intents
    }

    private fun filePickerIntent(): Intent {
        val intent: Intent
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            intent = Intent("com.sec.android.app.myfiles.PICK_DATA")
            intent.putExtra("CONTENT_TYPE", "*/*")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
        } else {
            intent = Intent(Intent.ACTION_GET_CONTENT) // or ACTION_OPEN_DOCUMENT
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toArray())
            intent.addCategory(Intent.CATEGORY_OPENABLE)
        }

        return intent
    }

    fun cameraIntent(): Intent {
        val mediaIntents = if (imagesAllowed()) {
            photoCameraIntents()
        }else {
            videoCameraIntents()
        }
        return mediaIntents.first()
    }

    @SuppressLint("IntentReset")
    fun chooserIntent(): Intent {
        val directCaptureIntents = arrayListOf<Intent>()
        if (imagesAllowed()) {
            directCaptureIntents.addAll(photoCameraIntents())
        }
        if (videosAllowed()) {
            directCaptureIntents.addAll(videoCameraIntents())
        }

        val chooserIntent: Intent?
        val mediaIntent: Intent?

        if (onlyImagesAndVideo() && !isGooglePhotosDefaultApp()) {
            mediaIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            mediaIntent.type = "image/*, video/*"
            mediaIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            mediaIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            chooserIntent = Intent.createChooser(mediaIntent, context.getString(R.string.choose_action))
        }else if (imagesAllowed() xor videosAllowed()) {
            mediaIntent = getMediaInitialIntent()
            mediaIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            chooserIntent = Intent.createChooser(mediaIntent, context.getString(R.string.choose_action))
        } else {
            chooserIntent = Intent.createChooser(filePickerIntent(), context.getString(R.string.choose_action))
        }
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, directCaptureIntents.toTypedArray<Parcelable>())

        return chooserIntent
    }

    private fun getMediaInitialIntent(): Intent {
        return if (imagesAllowed()) {
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        } else {
            Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
    }

    private fun onlyImagesAndVideo(): Boolean {
        return mimeTypes.all { it.startsWith("image/") || it.startsWith("video/") }
    }

    private fun isGooglePhotosDefaultApp(): Boolean {
        val captureIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val resolveList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManger.queryIntentActivities(captureIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            packageManger.queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        return resolveList.size == 1 && resolveList.first().activityInfo.packageName == "com.google.android.apps.photos"
    }
}