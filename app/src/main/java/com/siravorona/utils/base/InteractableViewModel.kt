package com.siravorona.utils.base

import com.siravorona.utils.weak

abstract class InteractableViewModel<TInteractor> : BaseViewModel(){
    protected var interactor by weak<TInteractor>()

    open fun init(interactor: TInteractor?) {
        this.interactor = interactor
    }
}