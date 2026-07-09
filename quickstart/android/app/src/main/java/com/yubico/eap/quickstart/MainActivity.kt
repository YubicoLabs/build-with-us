@file:OptIn(ExperimentalMaterial3Api::class)

package com.yubico.eap.quickstart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yubico.eap.quickstart.track.arkg.ARKGTrackView
import com.yubico.eap.quickstart.track.arkg.ARKGTrackViewModel
import com.yubico.eap.quickstart.track.credentials.CredentialTrackView
import com.yubico.eap.quickstart.track.credentials.CredentialTrackViewModel
import com.yubico.eap.quickstart.track.info.InfoTrackView
import com.yubico.eap.quickstart.track.info.InfoTrackViewModel
import com.yubico.eap.quickstart.track.signing.SigningTrackView
import com.yubico.eap.quickstart.track.signing.SigningTrackViewModel
import com.yubico.eap.quickstart.ui.YubicoGreen
import com.yubico.yubikit.fido.android.ui.FidoConfigManager
import com.yubico.yubikit.fido.client.extensions.CredBlobExtension
import com.yubico.yubikit.fido.client.extensions.CredPropsExtension
import com.yubico.yubikit.fido.client.extensions.CredProtectExtension
import com.yubico.yubikit.fido.client.extensions.HmacSecretExtension
import com.yubico.yubikit.fido.client.extensions.LargeBlobExtension
import com.yubico.yubikit.fido.client.extensions.MinPinLengthExtension
import com.yubico.yubikit.fido.client.extensions.SignExtension
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm by viewModels<QuickstartVM>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.setup(this)
        FidoConfigManager.update { it ->
            it.copy(
                fidoExtensions = listOf(
                    CredPropsExtension(),
                    CredBlobExtension(),
                    CredProtectExtension(),
                    HmacSecretExtension(),
                    MinPinLengthExtension(),
                    LargeBlobExtension(),
                    SignExtension()
                ),
                isCustomThemeEnabled = true,
                customTheme = { content -> YubiTheme(content) }
            )
        }

        setContent {
            var trackNumber by remember { vm.trackNumber }

            YubiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(), topBar = {
                        TopAppBar(
                            title = {
                                Text(getString(R.string.app_name))
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        when (trackNumber) {
                            null -> SelectTrackView(
                                tracks = listOf(
                                    stringResource(R.string.track_info),
                                    stringResource(R.string.track_credential),
                                    stringResource(R.string.track_sign),
                                    stringResource(R.string.track_arkg)
                                )
                            ) {
                                vm.startTrack(it, this@MainActivity)
                            }

                            0 -> InfoTrackView(
                                vm.trackVm.value as InfoTrackViewModel,
                                onCopyToClipBoard = vm::copyToClipBoard
                            ) {
                                vm.trackNumber.value = null
                            }

                            1 -> CredentialTrackView(
                                vm.trackVm.value as CredentialTrackViewModel,
                                onCopyToClipBoard = vm::copyToClipBoard
                            ) {
                                vm.trackNumber.value = null
                            }

                            2 -> SigningTrackView(
                                vm.trackVm.value as SigningTrackViewModel,
                                onCopyToClipBoard = vm::copyToClipBoard
                            ) {
                                vm.trackNumber.value = null
                            }

                            3 -> ARKGTrackView(
                                vm.trackVm.value as ARKGTrackViewModel,
                                onCopyToClipBoard = vm::copyToClipBoard
                            ) {
                                vm.trackNumber.value = null
                            }

                            else -> Text("Track $trackNumber not found.")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YubiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        if (isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }.copy(primary = YubicoGreen)
    ) { content() }
}

@Composable
fun SelectTrackView(
    tracks: List<String> = emptyList(),
    onTrackClicked: (Int) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .repeateableBackground(
                imageVector = R.drawable.ic_launcher_background,
                shape = RoundedCornerShape(
                    topStartPercent = 0,
                    topEndPercent = 0,
                    bottomStartPercent = 100,
                    bottomEndPercent = 100
                )
            )
    ) {
        item {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
                    .size(150.dp),
                painter = painterResource(R.mipmap.ic_launcher_foreground_trim),
                contentDescription = null
            )
        }

        items(tracks) { track ->
            OnboardingButton(
                title = track,
                onClick = { onTrackClicked(tracks.indexOf(track)) }
            )
        }
    }
}

@Composable
@Preview
private fun SelectTrackViewPreview() {
    SelectTrackView(
        tracks = listOf(
            "8 track",
            "9 track",
            "track attack",
        ),
    ) {}
}

@Composable
@Preview
private fun OnboardingButton(
    title: String = "test button",
    onClick: () -> Unit = {}
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp)
            .padding(16.dp),
        onClick = onClick
    ) {
        Text(text = title)
    }
}

@Composable
private fun Modifier.repeateableBackground(imageVector: Int, shape: Shape = RectangleShape): Modifier {
    val vector = ImageVector.vectorResource(imageVector)
    val painter = rememberVectorPainter(vector)
    val backgroundImage =
        painter.toImageBitmap(
            density = Density(density = 1f),
            layoutDirection = LayoutDirection.Ltr
        ).asAndroidBitmap()

    val imageBrush =
        ShaderBrush(
            ImageShader(
                image = backgroundImage.asImageBitmap(),
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated,
            )
        )

    return background(
        brush = imageBrush,
        shape = shape,
    )
}

fun Painter.toImageBitmap(
    density: Density,
    layoutDirection: LayoutDirection,
    size: Size = intrinsicSize,
    config: ImageBitmapConfig = ImageBitmapConfig.Argb8888,
): ImageBitmap {
    val image = ImageBitmap(
        width = size.width.roundToInt(),
        height = size.height.roundToInt(),
        config = config
    )
    val canvas = Canvas(image)

    CanvasDrawScope().draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = size
    ) {
        draw(size = this.size)
    }

    return image
}
