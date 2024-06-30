package com.siravorona.utils.activityresult

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/*
* https://dev.to/hichamboushaba/consuming-activity-results-using-coroutines-part-2-54mf
*  */

interface ActivityResultManager {
    companion object {
        fun getInstance(): ActivityResultManager = ActivityResultManagerImpl
        fun init(application: Application) {
            ActivityProvider.init(application)
        }
    }

    /**
     * Requests an Activity Result using the current visible Activity from the app.
     *
     * To allow handling process-death scenarios, the function checks if there is a pending result before
     * re-requesting a new one. So to handle this case, you just need to remember that there is a pending operation
     * in your [androidx.lifecycle.ViewModel] using a [androidx.lifecycle.SavedStateHandle],
     * then call the function another time when the app recovers to continue from where it left.
     *
     * @param contract the [androidx.activity.result.contract.ActivityResultContract] to use
     * @param input the input to pass when requesting the result, it needs to match the used [contract]
     *
     * @return the activity result
     */
    suspend fun <I, O, C : ActivityResultContract<I, O>> requestResult(contract: C, input: I): O?
}

suspend fun ActivityResultManager.getContent(vararg mimeTypes: String): Uri? {
    return getContent(mimeTypes.toList())
}
suspend fun ActivityResultManager.getContent(mimeTypes: List<String>? = null): Uri? {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        if (!mimeTypes.isNullOrEmpty()) {
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        }
    }
    val result = requestResult(ActivityResultContracts.StartActivityForResult(), intent)
    val uri = result?.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.data
    return uri
}

suspend fun ActivityResultManager.createDocument(mimeType: String, filename: String): Uri? {
    return requestResult(ActivityResultContracts.CreateDocument(mimeType), filename)
}

suspend fun ActivityResultManager.enableBluetooth(): Boolean {
    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    val result = requestResult(ActivityResultContracts.StartActivityForResult(), intent)
    val enabled = result?.resultCode == Activity.RESULT_OK
    return enabled
}
suspend fun ActivityResultManager.requestPermission(permission: String): Boolean {
    return requestResult(ActivityResultContracts.RequestPermission(), permission) ?: false
}

suspend fun ActivityResultManager.requestPermissions(permissions: Collection<String>): Map<String, Boolean>? {
    return requestResult(ActivityResultContracts.RequestMultiplePermissions(), permissions.toTypedArray())
}

suspend fun ActivityResultManager.openAppSettings(): Boolean {
    val packageName = ActivityProvider.applicationContext.packageName
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        data = Uri.fromParts("package", packageName, null)
    }
    val result = requestResult(ActivityResultContracts.StartActivityForResult(), intent)
    return result != null
}

suspend fun ActivityResultManager.takePicture(destination: Uri): Boolean {
    return requestResult(ActivityResultContracts.TakePicture(), destination) ?: false
}