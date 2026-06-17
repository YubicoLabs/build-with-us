package com.yubico.eap.quickstart.track.credentials

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yubico.eap.quickstart.track.InProgressView
import com.yubico.eap.quickstart.track.UserInformationView

@Composable
fun CredentialTrackView(
    vm: CredentialTrackViewModel,
    onCopyToClipBoard: (String) -> Unit,
    onFinished: () -> Unit,
) {
    val state by remember(vm) { vm.state }

    when (val typedState = state) {
        is CredentialTrackViewModel.State.InProgress -> InProgressView()

        is CredentialTrackViewModel.State.Error -> UserInformationView(
            title = typedState.title,
            message = typedState.message,
            informationItems = typedState.logs,
            onInformationSelected = { onCopyToClipBoard(typedState.logs[it]) },
            onCopyToClipBoard = onCopyToClipBoard,
            onFinished = onFinished,
        )

        is CredentialTrackViewModel.State.CredentialCreatedAndAsserted -> CredentialCreatedAndAttestedView(
            typedState.createCredentialOption,
            typedState.createCredentialResponse,
            typedState.assertCredentialOption,
            typedState.assertCredentialResponse,
            onFinished = onFinished,
        )
    }
}

@Preview
@Composable
fun CredentialCreatedAndAttestedViewPreview() {
    CredentialCreatedAndAttestedView(
        "createCredentialOption",
        "createCredentialResponse",
        "assertCredentialOption",
        "assertCredentialResponse",
    ) {}
}

@Composable
fun CredentialCreatedAndAttestedView(
    createCredentialOption: String,
    createCredentialResponse: String,
    assertCredentialOption: String,
    assertCredentialResponse: String,
    onFinished: () -> Unit
) {
    Card(
        modifier = Modifier.padding(32.dp),
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            LazyColumn {
                item {
                    Text(
                        style = MaterialTheme.typography.headlineLarge,
                        text = "Results"
                    )
                }

                item { CredentialRow("createCredentialOption", createCredentialOption) }
                item { CredentialRow("createCredentialResponse", createCredentialResponse) }
                item { CredentialRow("assertCredentialOption", assertCredentialOption) }
                item { CredentialRow("assertCredentialResponse", assertCredentialResponse) }

                item {
                    Row {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = onFinished) { Text(stringResource(android.R.string.ok)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialRow(
    title: String,
    content: String,
) {
    Text(
        style = MaterialTheme.typography.headlineSmall,
        text = title
    )
    Text(
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        text = content
    )
    Spacer(modifier = Modifier.height(32.dp))
}
