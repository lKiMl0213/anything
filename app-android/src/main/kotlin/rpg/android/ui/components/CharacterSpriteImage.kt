package rpg.android.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val CHARACTER_SPRITE_FALLBACK = "character_sprites/placeholder.png"

@Composable
fun CharacterSpriteImage(
    assetPath: String,
    modifier: Modifier = Modifier,
    imageWidthFraction: Float = 0.48f,
    maxWidth: androidx.compose.ui.unit.Dp = 220.dp,
    maxHeight: androidx.compose.ui.unit.Dp = 220.dp,
    visualScale: Float = 1f
) {
    if (assetPath.isBlank()) {
        return
    }

    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        loadCharacterSpriteBitmap(
            openAsset = { path -> context.assets.open(path) },
            assetPath = assetPath
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Sprite do personagem",
                modifier = Modifier
                    .fillMaxWidth(imageWidthFraction.coerceIn(0.10f, 1f))
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .scale(visualScale.coerceIn(0.5f, 3f)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

private fun loadCharacterSpriteBitmap(
    openAsset: (String) -> java.io.InputStream,
    assetPath: String
): ImageBitmap? {
    return runCatching {
        openAsset(assetPath).use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }.recoverCatching {
        openAsset(CHARACTER_SPRITE_FALLBACK).use { BitmapFactory.decodeStream(it).asImageBitmap() }
    }.getOrNull()
}
