package com.siravorona.utils.bindableproperty

import androidx.databinding.Observable

interface BindableObservable : Observable {
    fun notifyPropertyChanged(fieldId: Int)
}