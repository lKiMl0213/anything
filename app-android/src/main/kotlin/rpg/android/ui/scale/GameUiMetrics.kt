package rpg.android.ui.scale

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameUiMetrics(
    val scale: GameUiScale,
    val screenPadding: Dp,
    val panelPadding: Dp,
    val panelSpacing: Dp,
    val footerSpacing: Dp,
    val buttonMinHeight: Dp,
    val infoButtonHeight: Dp,
    val bottomNavHeight: Dp,
    val panelCorner: Dp,
    val buttonCorner: Dp,
    val slotCorner: Dp,
    val panelMaxWidth: Dp,
    val buttonMaxWidth: Dp,
    val compactSelectWidth: Dp,
    val iconButtonTouchSize: Dp,
    val backIconTouchSize: Dp,
    val iconButtonFontSize: TextUnit,
    val bottomNavIconSize: TextUnit,
    val titleTextSize: TextUnit,
    val bodyTextSize: TextUnit,
    val labelTextSize: TextUnit
) {
    companion object {
        fun forScale(scale: GameUiScale): GameUiMetrics {
            return when (scale) {
                GameUiScale.SMALL -> GameUiMetrics(
                    scale = scale,
                    screenPadding = 4.dp,
                    panelPadding = 2.dp,
                    panelSpacing = 4.dp,
                    footerSpacing = 5.dp,
                    buttonMinHeight = 30.dp,
                    infoButtonHeight = 44.dp,
                    bottomNavHeight = 44.dp,
                    panelCorner = 10.dp,
                    buttonCorner = 9.dp,
                    slotCorner = 9.dp,
                    panelMaxWidth = 500.dp,
                    buttonMaxWidth = 248.dp,
                    compactSelectWidth = 104.dp,
                    iconButtonTouchSize = 40.dp,
                    backIconTouchSize = 44.dp,
                    iconButtonFontSize = 26.sp,
                    bottomNavIconSize = 20.sp,
                    titleTextSize = 15.sp,
                    bodyTextSize = 13.sp,
                    labelTextSize = 11.sp
                )

                GameUiScale.MEDIUM -> GameUiMetrics(
                    scale = scale,
                    screenPadding = 6.dp,
                    panelPadding = 3.dp,
                    panelSpacing = 5.dp,
                    footerSpacing = 6.dp,
                    buttonMinHeight = 34.dp,
                    infoButtonHeight = 48.dp,
                    bottomNavHeight = 48.dp,
                    panelCorner = 12.dp,
                    buttonCorner = 10.dp,
                    slotCorner = 10.dp,
                    panelMaxWidth = 540.dp,
                    buttonMaxWidth = 280.dp,
                    compactSelectWidth = 116.dp,
                    iconButtonTouchSize = 44.dp,
                    backIconTouchSize = 48.dp,
                    iconButtonFontSize = 28.sp,
                    bottomNavIconSize = 22.sp,
                    titleTextSize = 16.sp,
                    bodyTextSize = 14.sp,
                    labelTextSize = 12.sp
                )

                GameUiScale.LARGE -> GameUiMetrics(
                    scale = scale,
                    screenPadding = 8.dp,
                    panelPadding = 4.dp,
                    panelSpacing = 6.dp,
                    footerSpacing = 8.dp,
                    buttonMinHeight = 38.dp,
                    infoButtonHeight = 52.dp,
                    bottomNavHeight = 54.dp,
                    panelCorner = 14.dp,
                    buttonCorner = 12.dp,
                    slotCorner = 12.dp,
                    panelMaxWidth = 620.dp,
                    buttonMaxWidth = 336.dp,
                    compactSelectWidth = 128.dp,
                    iconButtonTouchSize = 50.dp,
                    backIconTouchSize = 54.dp,
                    iconButtonFontSize = 32.sp,
                    bottomNavIconSize = 25.sp,
                    titleTextSize = 18.sp,
                    bodyTextSize = 15.sp,
                    labelTextSize = 13.sp
                )
            }
        }
    }
}
