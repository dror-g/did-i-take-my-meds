package com.siravorona.utils.listadapters

import androidx.databinding.ViewDataBinding

open class BaseTypeInfo
@JvmOverloads constructor(
    open val layout: Int,
    open val variable: Int? = null,
)

@Suppress("unused")
abstract class AbsTypeInfo<B : ViewDataBinding>
@JvmOverloads constructor(layout: Int, variable: Int? = null) : BaseTypeInfo(layout, variable)


open class ItemTypeInfo<B : ViewDataBinding>
@JvmOverloads constructor(layout: Int, variable: Int? = null) :
    AbsTypeInfo<B>(layout, variable) {
    open fun onCreate(holder: BaseBindableAdapter.Holder<B>) {}
    open fun onBind(holder: BaseBindableAdapter.Holder<B>) {}
    open fun onRecycle(holder: BaseBindableAdapter.Holder<B>) {}
}

open class ViewTypeInfo<B : ViewDataBinding>
@JvmOverloads constructor(
    layout: Int,
    variable: Int? = null,
) : AbsTypeInfo<B>(layout, variable) {
    internal var onCreate: Action<B>? = null; private set
    internal var onBind: Action<B>? = null; private set
    internal var onRecycle: Action<B>? = null; private set
    internal var onClick: Action<B>? = null; private set
    fun onCreate(action: Action<B>?) = apply { onCreate = action }
    fun onBind(action: Action<B>?) = apply { onBind = action }
    fun onRecycle(action: Action<B>?) = apply { onRecycle = action }
    fun onClick(action: Action<B>?) = apply { onClick = action }
}

typealias Action<B> = (BaseBindableAdapter.Holder<B>) -> Unit