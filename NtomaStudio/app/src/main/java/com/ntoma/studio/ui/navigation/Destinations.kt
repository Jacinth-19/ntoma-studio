package com.ntoma.studio.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val CREATE = "create"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"

    const val ONBOARDING = "onboarding"

    const val CAMERA_FABRIC = "camera/fabric"
    const val CAMERA_PERSON = "camera/person"

    fun edit(uri: String, target: String) = "edit/${Uri.encode(uri)}/$target"
    fun analysis(uri: String) = "analysis/${Uri.encode(uri)}"
    fun fabric(id: Long) = "fabric/$id"
    fun recommendations(fabricId: Long) = "recommendations/$fabricId"
    fun design(styleId: String) = "design/$styleId"
    fun tryOn(fabricId: Long?, styleId: String?, personPath: String? = null) =
        "tryon?fabricId=${fabricId ?: -1}&styleId=${styleId ?: ""}" +
            (personPath?.let { "&person=${Uri.encode(it)}" } ?: "")
    fun result(lookId: Long) = "result/$lookId"

    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val PRIVACY = "settings/privacy"
    const val DATA_CONTROLS = "settings/data"
    const val HELP = "settings/help"
    const val ABOUT = "settings/about"
    const val PREMIUM = "premium"
    fun legal(doc: String) = "legal/$doc"

    // Phase-2/4 destinations
    const val COLLECTIONS = "collections"
    fun collection(id: Long) = "collection/$id"
    const val WARDROBE = "wardrobe"
    fun colorMatch(fabricId: Long) = "colormatch/$fabricId"
    const val TAILORS = "tailors"
    fun tailor(id: String) = "tailor/$id"
    const val MEASUREMENTS = "measurements"

    val TOP_LEVEL = listOf(HOME, DISCOVER, CREATE, FAVORITES, PROFILE)
}
