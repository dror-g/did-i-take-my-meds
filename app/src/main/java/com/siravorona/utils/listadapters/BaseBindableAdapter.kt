package com.siravorona.utils.listadapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.OnRebindCallback
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

abstract class BaseBindableAdapter(
    protected val variable: Int? = null
) :RecyclerView.Adapter<BaseBindableAdapter.Holder<ViewDataBinding>>(){

    class Holder<B : ViewDataBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root) {
        internal var created = false
    }

    protected var recyclerView: RecyclerView? = null

    // inflater used for inflating views; initialized upon attaching to recycler view
    private lateinit var inflater: LayoutInflater

    // stores item view type information
    private val map = mutableMapOf<Class<*>, BaseTypeInfo>()

    private val DATA_INVALIDATION = Any()

    // region adapter settings methods

    // returns `this`, allowing chaining adapter settings calls
    fun into(recyclerView: RecyclerView) = apply { recyclerView.adapter = this }
    // returns `this`, allowing chaining adapter settings calls

    @JvmOverloads
    fun <T : Any> map(clazz: Class<T>, layout: Int, variable: Int? = null) =
        apply { map[clazz] = BaseTypeInfo(layout, variable) }

    // returns `this`, allowing chaining adapter settings calls
    inline fun <reified T : Any> map(layout: Int, variable: Int? = null) =
        map(T::class.java, layout, variable)


    // returns `this`, allowing chaining adapter settings calls
    fun <T : Any> map(clazz: Class<T>, type: AbsTypeInfo<*>) = apply { map[clazz] = type }

    inline fun <reified T : Any> map(type: AbsTypeInfo<*>) = map(T::class.java, type)

    // returns `this`, allowing chaining adapter settings calls
    inline fun <reified T : Any, B : ViewDataBinding> map(
        layout: Int,
        variable: Int? = null,
        noinline f: (ViewTypeInfo<B>.() -> Unit)? = null
    ) =
        map(T::class.java, ViewTypeInfo<B>(layout, variable).apply { f?.invoke(this) })
    // endregion

    // region abstract methods
    protected abstract fun getItemAtPosition(position: Int): Any?
    // endregion

    // region recyclerview adapter overrides
    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        recyclerView = rv
        inflater = LayoutInflater.from(rv.context)
    }

    override fun onDetachedFromRecyclerView(rv: RecyclerView) {
        recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<ViewDataBinding> {
        val binding = DataBindingUtil.inflate<ViewDataBinding>(inflater, viewType, parent, false)
        val holder = Holder(binding)
        binding?.addOnRebindCallback(object : OnRebindCallback<ViewDataBinding>() {
            override fun onPreBind(binding: ViewDataBinding) =
                recyclerView?.isComputingLayout ?: false

            override fun onCanceled(binding: ViewDataBinding) {
                if (recyclerView?.isComputingLayout != false) {
                    return
                }
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(position, DATA_INVALIDATION)
                }
            }
        })
        return holder
    }

    override fun onBindViewHolder(holder: Holder<ViewDataBinding>, position: Int) {
        val type = getType(position)!! //if this returned null, something went very wrong
        holder.binding.let {
            it.setVariable(getVariable(type), getItemAtPosition(position))
            it.executePendingBindings()
        }
        @Suppress("UNCHECKED_CAST")
        if (type is AbsTypeInfo<*>) {
            if (!holder.created) {
                notifyCreate(holder, type as AbsTypeInfo<ViewDataBinding>)
            }
            notifyBind(holder, type as AbsTypeInfo<ViewDataBinding>)
        }
    }

    override fun onBindViewHolder(
        holder: Holder<ViewDataBinding>,
        position: Int,
        payloads: List<Any>
    ) {
        if (shouldExecuteBindings(payloads)) {
            holder.binding.executePendingBindings()
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: Holder<ViewDataBinding>) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION && position < itemCount) {
            val type = getType(position)!! //if this returned null, something went very wrong
            if (type is AbsTypeInfo<*>) {
                @Suppress("UNCHECKED_CAST")
                notifyRecycle(holder, type as AbsTypeInfo<ViewDataBinding>)
            }
        }
    }



    override fun getItemViewType(position: Int) = getType(position)?.layout
        ?: throw RuntimeException("Invalid object at position $position: ${getItemAtPosition(position)?.javaClass}")
    // endregion

    // region item helpers
    protected open fun getType(position: Int): BaseTypeInfo? {
        return getItemAtPosition(position)?.let { map[it.javaClass] }
    }

    protected fun getVariable(type: BaseTypeInfo) = type.variable
        ?: variable
        ?: throw IllegalStateException("No variable specified for type ${type.javaClass.simpleName}")

    private fun shouldExecuteBindings(payloads: List<Any>): Boolean {
        if (payloads.isEmpty()) {
            return false
        }
        payloads.forEach {
            if (it != DATA_INVALIDATION) {
                return false
            }
        }
        return true
    }

    private fun setClickListeners(
        holder: Holder<ViewDataBinding>,
        type: ViewTypeInfo<ViewDataBinding>
    ) {
        val onClick = type.onClick
        if (onClick != null) {
            holder.itemView.setOnClickListener {
                onClick(holder)
            }
        }
        val onLongClick = type.onLongClick
        if (onLongClick != null) {
            holder.itemView.setOnLongClickListener {
                onLongClick(holder)
            }
        }
    }
    // endregion

    // region item lifecycle callbacks
    private fun notifyCreate(holder: Holder<ViewDataBinding>, type: AbsTypeInfo<ViewDataBinding>) {
        when (type) {
            is ViewTypeInfo -> {
                setClickListeners(holder, type)
                type.onCreate?.invoke(holder)
            }
            is ItemTypeInfo -> type.onCreate(holder)
        }
        holder.created = true
    }

    private fun notifyBind(holder: Holder<ViewDataBinding>, type: AbsTypeInfo<ViewDataBinding>) {
        when (type) {
            is ViewTypeInfo -> type.onBind?.invoke(holder)
            is ItemTypeInfo -> type.onBind(holder)
        }
    }

    private fun notifyRecycle(holder: Holder<ViewDataBinding>, type: AbsTypeInfo<ViewDataBinding>) {
        when (type) {
            is ViewTypeInfo -> type.onRecycle?.invoke(holder)
            is ItemTypeInfo -> type.onRecycle(holder)
        }
    }
    // endregion
}