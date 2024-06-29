package com.siravorona.utils.base

import androidx.annotation.Keep
import androidx.databinding.Observable
import androidx.databinding.PropertyChangeRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siravorona.utils.bindableproperty.BindableObservable
import com.siravorona.utils.decrementProgressCounter
import com.siravorona.utils.incrementProgressCounter
import com.siravorona.utils.launchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.reflect.KMutableProperty0

abstract class BaseViewModel : ViewModel(), Observable, BindableObservable {

    // region Observable methods
    @Transient
    private var mCallbacks: PropertyChangeRegistry? = null

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        synchronized(this) {
            if (mCallbacks == null) {
                mCallbacks = PropertyChangeRegistry()
            }
        }
        mCallbacks?.add(callback)
    }

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        synchronized(this) {
            if (mCallbacks == null) {
                return
            }
        }
        mCallbacks?.remove(callback)
    }

    @Keep
    fun notifyChange() {
        synchronized(this) {
            if (mCallbacks == null) {
                return
            }
        }
        mCallbacks?.notifyCallbacks(this, 0, null)
    }

    @Keep
    override fun notifyPropertyChanged(fieldId: Int) {
        synchronized(this) {
            if (mCallbacks == null) {
                return
            }
        }
        mCallbacks!!.notifyCallbacks(this, fieldId, null)
    }
    // endregion

    // region progress indicator helpers
    fun launchInScopeWithProgress(
        scope: CoroutineScope,
        progressProp: KMutableProperty0<Int>,
        action: suspend () -> Unit,
        onCatch: (suspend (Exception) -> Unit)? = null,
        onFinally: (suspend () -> Unit)? = null
    ): Job {
        progressProp.incrementProgressCounter()
        return launchScope(scope, action = {
            action.invoke()
        }, onCatch = onCatch, onFinally  = {
            progressProp.decrementProgressCounter()
            onFinally?.invoke()
        })
    }

    fun launchVmScopeWithProgress(
        progressProp: KMutableProperty0<Int>,
        action: suspend () -> Unit,
        onCatch: (suspend (Exception) -> Unit)? = null,
        onFinally: (suspend () -> Unit)? = null
    ) = launchInScopeWithProgress(viewModelScope, progressProp, action, onCatch, onFinally)
    // endregion

    // region other launch helpers

    fun launchVmScope(
        action: suspend () -> Unit,
        onCatch: (suspend (Exception) -> Unit)? = null,
        onFinally: (suspend () -> Unit)? = null
    ) = launchScope(viewModelScope, action, onCatch, onFinally)
    // endregion
}