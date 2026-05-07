package rpg.android.config

import rpg.android.BuildConfig

data class AppBuildInfo(
    val versionName: String,
    val versionCode: Long
) {
    val shortLabel: String
        get() = "v$versionName ($versionCode)"

    val betaLabel: String
        get() = "Beta $versionName | Build $versionCode"
}

object AppBuildInfoProvider {
    fun current(): AppBuildInfo {
        return AppBuildInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong()
        )
    }
}
