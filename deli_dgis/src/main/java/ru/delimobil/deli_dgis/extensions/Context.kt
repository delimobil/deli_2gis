package ru.delimobil.deli_dgis.extensions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat

fun Context.hasLocationPermission(): Boolean {
    return ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

fun Context.openForView(link: String): Boolean {
    return try {
        val uri = Uri.parse(link)
        val linkForOpen = if (uri.scheme == null) "http://$link" else link
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkForOpen))
        startActivity(intent)
        true
    } catch (e: Throwable) {
        false
    }
}

fun Context.getBitmapForDrawable(@DrawableRes resId: Int): Bitmap {
    val drawable = AppCompatResources.getDrawable(this, resId)!!
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    drawable.draw(Canvas(bitmap))
    return bitmap
}