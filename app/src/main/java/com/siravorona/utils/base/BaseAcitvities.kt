package com.siravorona.utils.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ViewDataBinding
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass


abstract class BaseBoundActivity<out TBinding : ViewBinding>(private val bindingClass: KClass<TBinding>) :
    AppCompatActivity() {
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