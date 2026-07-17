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

/// Shows the decrypted `encIdentifier` (device ID) and `encCredStoreState`, plus firmware, the
/// cache-invalidation outcome, and whether the token came from disk (no PIN).
struct DeviceInfoView: View {
    let report: DeviceIdentityReport

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Label(
                        report.reusedFromDisk ? "Reused saved token (no PIN)" : "Acquired with PIN",
                        systemImage: report.reusedFromDisk ? "arrow.clockwise.circle.fill" : "key.horizontal.fill"
                    )
                    .font(.subheadline).bold()
                    .foregroundStyle(report.reusedFromDisk ? .green : .blue)
                    Spacer()
                    Text("fw \(report.firmware)")
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                }

                field("Device ID (encIdentifier)", report.deviceIdHex)
                field("Cred store state (encCredStoreState)", report.credStoreStateHex)

                Label(report.cacheStatus.summary, systemImage: cacheIcon)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label("Device Identity", systemImage: "cpu")
        }
    }

    private var cacheIcon: String {
        switch report.cacheStatus {
        case .firstRun: return "tray.and.arrow.down"
        case .hit: return "bolt.fill"
        case .miss: return "arrow.triangle.2.circlepath"
        }
    }

    private func field(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value)
                .font(.system(.footnote, design: .monospaced))
                .textSelection(.enabled)
        }
    }
}
