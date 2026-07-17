// Copyright Yubico AB
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import SwiftUI

/// The landing screen: pick a transport, then either acquire a PPUAT with a PIN, or reuse the
/// token already saved in the Keychain (no PIN).
struct HomeView: View {
    @ObservedObject var viewModel: DeviceIdentityViewModel
    @State private var showingPINEntry = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header

                #if os(iOS)
                transportPicker
                #endif

                actions

                explainer
            }
            .padding()
        }
        .sheet(isPresented: $showingPINEntry) {
            PINEntryView { pin in
                showingPINEntry = false
                viewModel.acquireWithPIN(pin)
            } onCancel: {
                showingPINEntry = false
            }
        }
        .onAppear { viewModel.refreshSavedTokenState() }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Persistent PIN/UV Auth Token")
                .font(.title2).bold()
            Text(
                "Enter your PIN once to acquire a persistent token (PPUAT). "
                    + "The token is stored in the Keychain and reused on later launches with no PIN prompt."
            )
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
    }

    private var transportPicker: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Transport").font(.caption).foregroundStyle(.secondary)
            Picker("Transport", selection: $viewModel.transport) {
                ForEach(PPTransport.allCases) { transport in
                    Text(transport.label).tag(transport)
                }
            }
            .pickerStyle(.segmented)
        }
    }

    private var actions: some View {
        VStack(spacing: 12) {
            Button {
                showingPINEntry = true
            } label: {
                Label("Acquire PPUAT (enter PIN)", systemImage: "key.horizontal")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)

            Button {
                viewModel.reuseSavedToken()
            } label: {
                Label("Reuse Saved Token (no PIN)", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!viewModel.hasSavedToken)

            if viewModel.hasSavedToken {
                Button(role: .destructive) {
                    viewModel.clearSavedToken()
                } label: {
                    Label("Clear Saved Token", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var explainer: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(
                viewModel.hasSavedToken ? "A token is saved on this device." : "No token saved yet.",
                systemImage: viewModel.hasSavedToken ? "checkmark.seal" : "seal"
            )
            .font(.footnote)
            .foregroundStyle(viewModel.hasSavedToken ? .green : .secondary)

            Text("This quickstart demonstrates:")
                .font(.footnote).bold()
            bullet("Feature detection (firmware 5.8+, PCMR support)")
            bullet("Decrypting encIdentifier → stable device ID")
            bullet("Decrypting encCredStoreState → cache invalidation")
            bullet("Enumerating relying parties and credentials")
            bullet("Cross-session token reuse from disk (no PIN)")
            bullet("Read-only scope probe (delete is rejected)")
        }
        .padding(.top, 8)
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
            Text(text)
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
    }
}
