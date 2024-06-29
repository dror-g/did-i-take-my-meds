package dev.corruptedark.diditakemymeds.activities.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
