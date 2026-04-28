package com.yubico.eap.quickstart.track

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.yubico.eap.quickstart.ui.YubicoGreen


@Composable
fun UserInformationView(
    title: String,
    message: String,
    informationItems: List<String>,
    confirmationButtonTitle: String = stringResource(android.R.string.cancel),
    onInformationSelected: ((index: Int) -> Unit)? = null,
    onCopyToClipBoard: ((String) -> Unit)? = null,
    onFinished: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onFinished,
        confirmButton = {
            Button(onClick = onFinished) {
                Text(confirmationButtonTitle)
            }
        },
        dismissButton = {
            Row {
                onCopyToClipBoard?.let {
                    Button(
                        onClick = { it(informationItems.joinToString("\n")) },
                    ) {
                        Text(
                            modifier = Modifier.graphicsLayer {
                                colorFilter = ColorFilter.lighting(YubicoGreen, Color(0xFF000000))
                            },
                            text = "📋",
                        )
                    }
                }
            }
        },
        properties = DialogProperties(),
        title = { Text(text = title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = message
                    )
                }
                itemsIndexed(informationItems) { index, information ->
                    val color = if (index % 2 == 0) {
                        Color(0x20ffffff)
                    } else {
                        Color(0x10ffffff)
                    }

                    Box(
                        modifier = Modifier.Companion
                            .defaultMinSize(minHeight = 48.dp)
                            .fillMaxWidth()
                            .background(color),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            modifier = onInformationSelected?.let {
                                Modifier.clickable { onInformationSelected(index) }
                            } ?: Modifier.Companion,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            text = information
                        )
                    }
                }
            }
        }
    )
}
