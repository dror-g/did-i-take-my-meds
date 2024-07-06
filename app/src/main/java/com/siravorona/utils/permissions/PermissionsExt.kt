package com.siravorona.utils.permissions

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

fun Context.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Context.allPermissionsGranted(permissions: Collection<String>): Boolean {
    return permissions.all(this::isPermissionGranted)
}

fun Context.getGrantedPermissions(permissions: Collection<String>): List<String> {
    return permissions.filter { isPermissionGranted(it) }
}
