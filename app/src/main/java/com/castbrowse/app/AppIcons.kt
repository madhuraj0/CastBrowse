package com.castbrowse.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Standard Material Design 3 Expressive Vector Icons
 * Handcrafted to eliminate emoji usage and avoid the heavy material-icons-extended dependency.
 */
object AppIcons {

    // Browser Globe / Language Icon (Material 3 Language)
    val Browser: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(17.92f, 8f)
            horizontalLineTo(14.96f)
            curveTo(14.63f, 6.69f, 14.12f, 5.46f, 13.46f, 4.35f)
            curveTo(15.4f, 5.09f, 16.99f, 6.37f, 17.92f, 8f)
            close()
            moveTo(12f, 4.04f)
            curveTo(12.79f, 5.21f, 13.39f, 6.55f, 13.73f, 8f)
            horizontalLineTo(10.27f)
            curveTo(10.61f, 6.55f, 11.21f, 5.21f, 12f, 4.04f)
            close()
            moveTo(4.26f, 14f)
            curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
            curveTo(4f, 11.31f, 4.1f, 10.64f, 4.26f, 10f)
            horizontalLineTo(7.64f)
            curveTo(7.56f, 10.65f, 7.51f, 11.32f, 7.51f, 12f)
            curveTo(7.51f, 12.68f, 7.56f, 13.35f, 7.64f, 14f)
            horizontalLineTo(4.26f)
            close()
            moveTo(5.08f, 16f)
            horizontalLineTo(8.04f)
            curveTo(8.37f, 17.31f, 8.88f, 18.54f, 9.54f, 19.65f)
            curveTo(7.6f, 18.91f, 6.01f, 17.63f, 5.08f, 16f)
            close()
            moveTo(8.04f, 8f)
            horizontalLineTo(5.08f)
            curveTo(6.01f, 6.37f, 7.6f, 5.09f, 9.54f, 4.35f)
            curveTo(8.88f, 5.46f, 8.37f, 6.69f, 8.04f, 8f)
            close()
            moveTo(12f, 19.96f)
            curveTo(11.21f, 18.79f, 10.61f, 17.45f, 10.27f, 16f)
            horizontalLineTo(13.73f)
            curveTo(13.39f, 17.45f, 12.79f, 18.79f, 12f, 19.96f)
            close()
            moveTo(14.34f, 14f)
            horizontalLineTo(9.66f)
            curveTo(9.57f, 13.34f, 9.51f, 12.68f, 9.51f, 12f)
            curveTo(9.51f, 11.32f, 9.57f, 10.66f, 9.66f, 10f)
            horizontalLineTo(14.34f)
            curveTo(14.43f, 10.66f, 14.49f, 11.32f, 14.49f, 12f)
            curveTo(14.49f, 12.68f, 14.43f, 13.34f, 14.34f, 14f)
            close()
            moveTo(13.46f, 19.65f)
            curveTo(14.12f, 18.54f, 14.63f, 17.31f, 14.96f, 16f)
            horizontalLineTo(17.92f)
            curveTo(16.99f, 17.63f, 15.4f, 18.91f, 13.46f, 19.65f)
            close()
            moveTo(16.36f, 14f)
            curveTo(16.44f, 13.35f, 16.49f, 12.68f, 16.49f, 12f)
            curveTo(16.49f, 11.32f, 16.44f, 10.65f, 16.36f, 10f)
            horizontalLineTo(19.74f)
            curveTo(19.9f, 10.64f, 20f, 11.31f, 20f, 12f)
            curveTo(20f, 12.69f, 19.9f, 13.36f, 19.74f, 14f)
            horizontalLineTo(16.36f)
            close()
        }.build()
    }

    // Media Library Icon (Video / Audio Hub)
    val MediaLibrary: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(4f, 6f)
            horizontalLineTo(2f)
            verticalLineTo(20f)
            curveTo(2f, 21.1f, 2.9f, 22f, 4f, 22f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            close()
            moveTo(20f, 2f)
            horizontalLineTo(8f)
            curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f)
            verticalLineTo(16f)
            curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f)
            horizontalLineTo(20f)
            curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            verticalLineTo(4f)
            curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
            moveTo(12f, 14.5f)
            verticalLineTo(5.5f)
            lineTo(18f, 10f)
            lineTo(12f, 14.5f)
            close()
        }.build()
    }

    // Folder Open Icon
    val FolderOpen: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(20f, 6f)
            horizontalLineTo(12f)
            lineTo(10f, 4f)
            horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
            horizontalLineTo(20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            verticalLineTo(8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
            close()
            moveTo(20f, 18f)
            horizontalLineTo(4f)
            verticalLineTo(8f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            close()
        }.build()
    }

    // Tune / Filter Icon (Material 3 Tune)
    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(3f, 17f)
            verticalLineTo(19f)
            horizontalLineTo(9f)
            verticalLineTo(17f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineTo(19f)
            horizontalLineTo(21f)
            verticalLineTo(17f)
            horizontalLineTo(13f)
            verticalLineTo(15f)
            horizontalLineTo(11f)
            verticalLineTo(21f)
            horizontalLineTo(13f)
            close()
            moveTo(7f, 9f)
            verticalLineTo(11f)
            horizontalLineTo(3f)
            verticalLineTo(13f)
            horizontalLineTo(7f)
            verticalLineTo(15f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(21f, 13f)
            verticalLineTo(11f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(21f)
            close()
            moveTo(17f, 9f)
            horizontalLineTo(19f)
            verticalLineTo(7f)
            horizontalLineTo(21f)
            verticalLineTo(5f)
            horizontalLineTo(19f)
            verticalLineTo(3f)
            horizontalLineTo(17f)
            verticalLineTo(9f)
            close()
        }.build()
    }

    // TV Icon
    val Tv: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(21f, 3f)
            horizontalLineTo(3f)
            curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
            verticalLineTo(17f)
            curveTo(1f, 18.1f, 1.9f, 19f, 3f, 19f)
            horizontalLineTo(8f)
            verticalLineTo(21f)
            horizontalLineTo(16f)
            verticalLineTo(19f)
            horizontalLineTo(21f)
            curveTo(22.1f, 19f, 23f, 18.1f, 23f, 17f)
            verticalLineTo(5f)
            curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f)
            close()
            moveTo(21f, 17f)
            horizontalLineTo(3f)
            verticalLineTo(5f)
            horizontalLineTo(21f)
            verticalLineTo(17f)
            close()
        }.build()
    }

    // Lightbulb Icon
    val Lightbulb: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(9f, 21f)
            curveTo(9f, 21.55f, 9.45f, 22f, 10f, 22f)
            horizontalLineTo(14f)
            curveTo(14.55f, 22f, 15f, 21.55f, 15f, 21f)
            verticalLineTo(20f)
            horizontalLineTo(9f)
            verticalLineTo(21f)
            close()
            moveTo(12f, 2f)
            curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
            curveTo(5f, 11.38f, 6.19f, 13.47f, 8f, 14.74f)
            verticalLineTo(17f)
            curveTo(8f, 17.55f, 8.45f, 18f, 9f, 18f)
            horizontalLineTo(15f)
            curveTo(15.55f, 18f, 16f, 17.55f, 16f, 17f)
            verticalLineTo(14.74f)
            curveTo(17.81f, 13.47f, 19f, 11.38f, 19f, 9f)
            curveTo(19f, 5.14f, 15.86f, 2f, 12f, 2f)
            close()
        }.build()
    }

    // Light Mode (Sun) Icon
    val LightMode: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
            moveTo(12f, 9f)
            curveTo(13.66f, 9f, 15f, 10.34f, 15f, 12f)
            curveTo(15f, 13.66f, 13.66f, 15f, 12f, 15f)
            curveTo(10.34f, 15f, 9f, 13.66f, 9f, 12f)
            curveTo(9f, 10.34f, 10.34f, 9f, 12f, 9f)
            close()
            moveTo(11f, 1f)
            horizontalLineTo(13f)
            verticalLineTo(4f)
            horizontalLineTo(11f)
            close()
            moveTo(11f, 20f)
            horizontalLineTo(13f)
            verticalLineTo(23f)
            horizontalLineTo(11f)
            close()
            moveTo(3.515f, 4.929f)
            lineTo(4.929f, 3.515f)
            lineTo(7.05f, 5.636f)
            lineTo(5.636f, 7.05f)
            close()
            moveTo(16.95f, 18.364f)
            lineTo(18.364f, 16.95f)
            lineTo(20.485f, 19.071f)
            lineTo(19.071f, 20.485f)
            close()
            moveTo(1f, 11f)
            horizontalLineTo(4f)
            verticalLineTo(13f)
            horizontalLineTo(1f)
            close()
            moveTo(20f, 11f)
            horizontalLineTo(23f)
            verticalLineTo(13f)
            horizontalLineTo(20f)
            close()
            moveTo(5.636f, 16.95f)
            lineTo(7.05f, 18.364f)
            lineTo(4.929f, 20.485f)
            lineTo(3.515f, 19.071f)
            close()
            moveTo(19.071f, 3.515f)
            lineTo(20.485f, 4.929f)
            lineTo(18.364f, 7.05f)
            lineTo(16.95f, 5.636f)
            close()
        }.build()
    }

    // Dark Mode (Moon) Icon
    val DarkMode: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12.3f, 2f)
            curveTo(6.64f, 2.15f, 2.15f, 6.64f, 2f, 12.3f)
            curveTo(1.82f, 17.91f, 6.39f, 22.58f, 12f, 22.58f)
            curveTo(12.43f, 22.58f, 12.87f, 22.54f, 13.3f, 22.47f)
            curveTo(12.16f, 20.73f, 11.5f, 18.66f, 11.5f, 16.42f)
            curveTo(11.5f, 10.42f, 16.42f, 5.5f, 22.42f, 5.5f)
            curveTo(22.65f, 5.5f, 22.88f, 5.51f, 23.11f, 5.53f)
            curveTo(20.78f, 3.32f, 17.65f, 2f, 14.19f, 2f)
            curveTo(13.56f, 2f, 12.93f, 2.07f, 12.3f, 2f)
            close()
        }.build()
    }

    // Contrast / OLED Black Icon
    val Contrast: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            verticalLineTo(4f)
            curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
            curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
            close()
        }.build()
    }

    // AutoAwesome / Sparkles Icon (Dynamic Color)
    val AutoAwesome: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(19f, 9f)
            lineTo(20.25f, 6.25f)
            lineTo(23f, 5f)
            lineTo(20.25f, 3.75f)
            lineTo(19f, 1f)
            lineTo(17.75f, 3.75f)
            lineTo(15f, 5f)
            lineTo(17.75f, 6.25f)
            close()
            moveTo(19f, 15f)
            lineTo(17.75f, 17.75f)
            lineTo(15f, 19f)
            lineTo(17.75f, 20.25f)
            lineTo(19f, 23f)
            lineTo(20.25f, 20.25f)
            lineTo(23f, 19f)
            lineTo(20.25f, 17.75f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineTo(6.5f, 14.5f)
            lineTo(9f, 20f)
            lineTo(11.5f, 14.5f)
            lineTo(17f, 12f)
            close()
        }.build()
    }

    // Block / Exclude Icon
    val Block: ImageVector by lazy {
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
            close()
            moveTo(4f, 12f)
            curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
            curveTo(13.85f, 4f, 15.55f, 4.63f, 16.9f, 5.69f)
            lineTo(5.69f, 16.9f)
            curveTo(4.63f, 15.55f, 4f, 13.85f, 4f, 12f)
            close()
            moveTo(12f, 20f)
            curveTo(10.15f, 20f, 8.45f, 19.37f, 7.1f, 18.31f)
            lineTo(18.31f, 7.1f)
            curveTo(19.37f, 8.45f, 20f, 10.15f, 20f, 12f)
            curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
            close()
        }.build()
    }
}
