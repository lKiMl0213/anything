package rpg.android.platform

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

data class AndroidGamePaths(
    val dataRoot: Path,
    val savesRoot: Path
)

object AndroidDataBootstrap {
    private val preservedRootDirs = setOf("saves")

    fun prepare(context: Context): AndroidGamePaths {
        val dataRoot = context.filesDir.toPath().resolve("game-data")
        val savesRoot = dataRoot.resolve("saves")
        if (!Files.exists(dataRoot.resolve("balance.json"))) {
            dataRoot.createDirectories()
            copyAssetsTree(context.assets, "", dataRoot.toFile())
        } else {
            syncDataAssets(context.assets, dataRoot.toFile())
        }
        savesRoot.createDirectories()
        purgeLegacyAutosaves(savesRoot.toFile())
        return AndroidGamePaths(dataRoot = dataRoot, savesRoot = savesRoot)
    }

    private fun copyAssetsTree(assets: AssetManager, assetPath: String, target: File) {
        val entries = assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            copyFile(assets, assetPath, target)
            return
        }

        if (!target.exists()) {
            target.mkdirs()
        }
        entries.forEach { entry ->
            val nextAssetPath = if (assetPath.isBlank()) entry else "$assetPath/$entry"
            val nextTarget = File(target, entry)
            copyAssetsTree(assets, nextAssetPath, nextTarget)
        }
    }

    private fun copyFile(assets: AssetManager, assetPath: String, target: File) {
        target.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun syncDataAssets(assets: AssetManager, dataRoot: File) {
        runCatching {
            mirrorAssetsTree(
                assets = assets,
                assetPath = "",
                target = dataRoot,
                preserveAtRoot = preservedRootDirs
            )
        }
    }

    private fun purgeLegacyAutosaves(savesRoot: File) {
        val saveFiles = savesRoot.listFiles().orEmpty()
        saveFiles.forEach { file ->
            val name = file.name.lowercase()
            val isLegacyAutosave = name == "autosave.json" || name.startsWith("autosave_")
            if (file.isFile && isLegacyAutosave) {
                runCatching { file.delete() }
            }
        }
    }

    private fun mirrorAssetsTree(
        assets: AssetManager,
        assetPath: String,
        target: File,
        preserveAtRoot: Set<String>
    ) {
        val entries = assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            copyFile(assets, assetPath, target)
            return
        }

        if (!target.exists()) {
            target.mkdirs()
        }

        val assetEntrySet = entries.toSet()
        target.listFiles().orEmpty().forEach { localChild ->
            if (assetPath.isBlank() && localChild.name in preserveAtRoot) {
                return@forEach
            }
            if (localChild.name !in assetEntrySet) {
                runCatching { localChild.deleteRecursively() }
            }
        }

        entries.forEach { entry ->
            val nextAssetPath = if (assetPath.isBlank()) entry else "$assetPath/$entry"
            val nextTarget = File(target, entry)
            mirrorAssetsTree(
                assets = assets,
                assetPath = nextAssetPath,
                target = nextTarget,
                preserveAtRoot = emptySet()
            )
        }
    }
}
