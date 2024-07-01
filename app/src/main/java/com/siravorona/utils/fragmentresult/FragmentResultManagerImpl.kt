@file:OptIn(ExperimentalCoroutinesApi::class)

package com.siravorona.utils.fragmentresult

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

object FragmentResultManagerImpl : FragmentResultManager {
    override suspend fun requestResult(
        fragmentManager: FragmentManager,
        requestKey: String,
        lifecycleOwner: LifecycleOwner,
    ): Bundle {
        return suspendCancellableCoroutine { continuation ->
            fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner) { key, bundle ->
                fragmentManager.clearFragmentResultListener(key)
                continuation.resume(bundle, null)
            }
        }
    }

    override suspend fun addFragmentForResult(
        fragmentManager: FragmentManager,
        fragment: Fragment,
        containerViewId: Int,
        fragmentTag: String?,
        requestKey: String,
        lifecycleOwner: LifecycleOwner) : Bundle {

        return suspendCancellableCoroutine { continuation ->

            fragmentManager.beginTransaction()
                .add(containerViewId, fragment, fragmentTag)
                .commit()
            fragmentManager.setFragmentResultListener(requestKey, lifecycleOwner) { key, bundle ->
                fragmentManager.clearFragmentResult(key)
                continuation.resume(bundle, null)
            }
        }
    }
}