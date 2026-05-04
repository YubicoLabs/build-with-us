package com.yubico.eap.quickstart.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yubico.eap.quickstart.R

@Composable
fun SuccessView(
    message: String,
    messageHash: ByteArray,
    signature: ByteArray,
    verified: Boolean,
    onFinished: () -> Unit
) {
    Card(
        modifier = Modifier.Companion
            .padding(32.dp),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn {
                item {
                    Text(
                        style = MaterialTheme.typography.headlineMedium,
                        text = stringResource(R.string.track_one_success_message_title)
                    )
                }
                item { Text(style = MaterialTheme.typography.bodySmall, text = message) }
                item { Text(style = MaterialTheme.typography.bodySmall, text = messageHash.toHexString()) }
                item {
                    Text(
                        style = MaterialTheme.typography.headlineMedium,
                        text = stringResource(R.string.track_one_success_signature_title)
                    )
                }
                item { Text(style = MaterialTheme.typography.bodySmall, text = signature.toHexString()) }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    Text(
                        style = MaterialTheme.typography.headlineMedium,
                        text = stringResource(R.string.track_one_success_verified_title)
                    )
                }
                item {
                    Text(
                        modifier = Modifier.background(Color(if (verified) 0x3300FF00 else 0x33FF0000)),
                        style = MaterialTheme.typography.bodySmall,
                        text = if (verified) "Yes 👍" else "No 👎"
                    )
                }
                item {
                    Row {
                        Spacer(modifier = Modifier.weight(1f)); Button(onClick = onFinished) {
                        Text(
                            stringResource(
                                android.R.string.ok
                            )
                        )
                    }
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SuccessViewPreview() {
    SuccessView(
        message = "HelloWorld",
        messageHash = "HelloWorld".toByteArray(),
        signature = "SIGNSTUAADS".toByteArray(),
        verified = true,
    ) { }
}
