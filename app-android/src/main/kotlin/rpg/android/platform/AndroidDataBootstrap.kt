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
    private const val PATCH_NOTES_ASSET = "patchnotes/changelog.json"

    fun prepare(context: Context): AndroidGamePaths {
        val dataRoot = context.filesDir.toPath().resolve("game-data")
        val savesRoot = dataRoot.resolve("saves")
        if (!Files.exists(dataRoot.resolve("balance.json"))) {
            dataRoot.createDirectories()
            copyAssetsTree(context.assets, "", dataRoot.toFile())
        } else {
            syncPatchNotesAsset(context.assets, dataRoot.toFile())
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

    private fun syncPatchNotesAsset(assets: AssetManager, dataRoot: File) {
        val target = File(dataRoot, PATCH_NOTES_ASSET)
        runCatching {
            copyFile(assets, PATCH_NOTES_ASSET, target)
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
}
