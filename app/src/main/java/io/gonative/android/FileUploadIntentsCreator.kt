package io.gonative.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.gonative.gonative_core.AppConfig
import io.gonative.gonative_core.Utils
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

    fun imagesAllowed(): Boolean {
        if (!Utils.isPermissionGranted(context as Activity, android.Manifest.permission.CAMERA)) return false
        return mimeTypes.contains("*/*") || mimeTypes.any { it.contains("image/") }
    }

    fun videosAllowed(): Boolean {
        if (!Utils.isPermissionGranted(context as Activity, android.Manifest.permission.CAMERA)) return false
        return mimeTypes.contains("*/*") || mimeTypes.any { it.contains("video/") }
    }

    private fun photoCameraIntents(): ArrayList<Intent> {
        val intents = arrayListOf<Intent>()

        if (!appConfig.directCameraUploads) {
            return intents
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "IMG_$timeStamp.jpg"
        val storageDir = this.context.filesDir
        val captureFile = File(storageDir, imageFileName)

        currentCaptureUri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".fileprovider", captureFile);

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val resolveList: List<ResolveInfo> = listOfAvailableAppsForIntent(captureIntent)
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
        val resolveList: List<ResolveInfo> = listOfAvailableAppsForIntent(captureIntent)
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
        var intent: Intent
        intent = Intent(Intent.ACTION_GET_CONTENT) // or ACTION_OPEN_DOCUMENT
        intent.type = mimeTypes.joinToString(", ")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        val resolveList: List<ResolveInfo> = listOfAvailableAppsForIntent(intent)

        if (resolveList.isEmpty() && Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            intent = Intent("com.sec.android.app.myfiles.PICK_DATA")
            intent.putExtra("CONTENT_TYPE", "*/*")
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            return intent
        }

        return intent
    }

    fun cameraIntent(): Intent {
        val mediaIntents = if (imagesAllowed()) {
            photoCameraIntents()
        } else {
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

        if (imagesAllowed() xor videosAllowed()) {
            mediaIntent = getMediaInitialIntent()
            mediaIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            chooserIntent = Intent.createChooser(mediaIntent, context.getString(R.string.choose_action))
        } else if (onlyImagesAndVideo() && !isGooglePhotosDefaultApp()) {
            mediaIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            mediaIntent.type = "image/*, video/*"
            mediaIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
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
        val resolveList: List<ResolveInfo> = listOfAvailableAppsForIntent(captureIntent)

        return resolveList.size == 1 && resolveList.first().activityInfo.packageName == "com.google.android.apps.photos"
    }

    private fun listOfAvailableAppsForIntent(intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManger.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManger.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }
}