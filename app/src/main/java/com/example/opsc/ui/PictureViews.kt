package com.example.opsc.ui

import android.content.res.ColorStateList
import coil3.load
import coil3.request.crossfade
import com.example.opsc.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import java.io.File

/** Shows a picture from a file with Coil, or a default: a person icon for an avatar, nothing for a banner. The cache key includes the file age, so a replaced picture shows at once. */
fun ShapeableImageView.showPicture(file: File?, avatar: Boolean) {
    if (file != null) {
        setPadding(0, 0, 0, 0)
        imageTintList = null
        load(file) {
            crossfade(true)
            memoryCacheKey("${file.path}-${file.lastModified()}")
        }
    } else if (avatar) {
        val padding = resources.getDimensionPixelSize(R.dimen.space_lg)
        setPadding(padding, padding, padding, padding)
        imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
        setImageResource(R.drawable.ic_nav_profile)
    } else {
        imageTintList = null
        setImageDrawable(null)
    }
}
