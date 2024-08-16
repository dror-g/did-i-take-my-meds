package com.siravorona.utils.base

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ViewDataBinding
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

typealias BackPressedCallback = () -> Boolean

abstract class BaseActivity : AppCompatActivity() {
    protected var backPressedCallback: BackPressedCallback? = null

    fun setOnBackPressListener(block: BackPressedCallback) {
        backPressedCallback = block

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(1000) {
                if (block()) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (backPressedCallback?.invoke() == false) {
                return
            }
        }
        super.onBackPressed()
    }
}

abstract class BaseBoundActivity<out TBinding : ViewBinding>(private val bindingClass: KClass<TBinding>) :
    BaseActivity() {
    private lateinit var __binding: TBinding
    protected val binding
        get() = __binding

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val func = bindingClass.members.first { it.name == "inflate" }
        __binding = func.call(layoutInflater) as TBinding
        setContentView(binding.root)
    }
}

abstract class BaseBoundVmActivity<out TBinding : ViewDataBinding, out TViewModel : BaseViewModel>(
    bindingClass: KClass<TBinding>,
    private val bindingVariable: Int,
) : BaseBoundActivity<TBinding>(bindingClass) {
    protected abstract val vm: TViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.setVariable(bindingVariable, vm)
    }
}

abstract class BaseBoundInteractableVmActivity<out TBinding : ViewDataBinding, out TViewModel : InteractableViewModel<TInteractor>, TInteractor : Any>(
    bindingClass: KClass<TBinding>,
    bindingVariable: Int,
) : BaseBoundVmActivity<TBinding, TViewModel>(bindingClass, bindingVariable) {
    protected abstract val modelInteractor: TInteractor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.init(modelInteractor)
    }
}