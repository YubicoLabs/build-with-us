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

/// Collects the FIDO2 PIN a single time to acquire the PPUAT.
struct PINEntryView: View {
    let onSubmit: (String) -> Void
    let onCancel: () -> Void

    @State private var pin = ""

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "lock.shield")
                .font(.system(size: 44))
                .foregroundStyle(.tint)

            Text("Enter FIDO2 PIN")
                .font(.title3).bold()

            Text("Your PIN is used once to acquire the persistent token.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            SecureField("PIN", text: $pin)
                .textFieldStyle(.roundedBorder)
                #if os(iOS)
                .keyboardType(.numberPad)
                #endif
                .frame(maxWidth: 240)

            HStack {
                Button("Cancel", role: .cancel, action: onCancel)
                    .buttonStyle(.bordered)
                Button("Acquire") {
                    onSubmit(pin)
                }
                .buttonStyle(.borderedProminent)
                .disabled(pin.isEmpty)
            }
        }
        .padding(28)
        .frame(minWidth: 320)
    }
}
