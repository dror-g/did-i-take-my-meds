package com.siravorona.utils.fragmentresult

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import java.io.Serializable

interface FragmentResultManager {
    companion object {
        fun getInstance() = FragmentResultManagerImpl
    }

    suspend fun requestResult(
        fragmentManager: FragmentManager,
        requestKey: String,
        lifecycleOwner: LifecycleOwner,
    ): Bundle

    suspend fun addFragmentForResult(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        containerViewId: Int,
        fragmentTag: String?,
        requestKey: String,
        lifecycleOwner: LifecycleOwner,
    ): Bundle
}


suspend fun <T : Serializable> FragmentResultManager.addFragmentForResult(
    activity: FragmentActivity,
    fragment: Fragment,
    fragmentTag: String?,
    requestKey: String
): Bundle {
    return addFragmentForResult(activity.supportFragmentManager, fragment, android.R.id.content, fragmentTag, requestKey, activity)
}


suspend fun <T : Serializable> FragmentResultManager.addFragmentForResult(
    activity: FragmentActivity,
    fragment: Fragment,
    fragmentTag: String?,
    requestKey: String,
    resultKey: String
): T? {
    val bundle = addFragmentForResult(activity.supportFragmentManager, fragment, android.R.id.content, fragmentTag, requestKey, activity)
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return bundle.getSerializable(resultKey) as? T
}

suspend fun <T : Serializable> FragmentResultManager.requestResult(
    fragmentManager: FragmentManager,
    requestKey: String,
    resultKey: String,
    lifecycleOwner: LifecycleOwner,
): T? {
    val bundle = requestResult(fragmentManager, requestKey, lifecycleOwner)
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return bundle.getSerializable(resultKey) as? T
}

suspend fun <T : Serializable> FragmentResultManager.addFragmentForResult(
    fragmentManager: FragmentManager,
    fragment: Fragment,
    containerViewId: Int,
    fragmentTag: String?,
    requestKey: String,
    resultKey: String,
    lifecycleOwner: LifecycleOwner
): T? {
    val bundle = addFragmentForResult(fragmentManager, fragment, containerViewId, fragmentTag, requestKey, lifecycleOwner)
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return bundle.getSerializable(resultKey) as? T
}

suspend fun <T : Serializable> FragmentResultManager.showDialogFragmentForResult(
    fragmentManager: FragmentManager,
    fragment: DialogFragment,
    fragmentTag: String?,
    requestKey: String,
    resultKey: String,
    lifecycleOwner: LifecycleOwner
): T? {
    fragment.show(fragmentManager, fragmentTag)
    val bundle = requestResult(fragmentManager, requestKey, lifecycleOwner)
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    return bundle.getSerializable(resultKey) as? T
}
