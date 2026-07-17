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

/// Lists metadata counts, relying parties, and the credentials enumerated with the PPUAT.
struct CredentialListView: View {
    let report: DeviceIdentityReport

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    counter("Discoverable", report.existingCredentialsCount)
                    Divider().frame(height: 28)
                    counter("Remaining slots", report.maxRemainingCredentialsCount)
                }

                if report.relyingParties.isEmpty {
                    Text("No discoverable credentials on this key.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(report.relyingParties) { rp in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(rp.rpName ?? rp.rpId)
                                .font(.subheadline).bold()
                            if rp.rpName != nil {
                                Text(rp.rpId).font(.caption).foregroundStyle(.secondary)
                            }
                            ForEach(rp.credentials) { cred in
                                HStack(alignment: .top, spacing: 6) {
                                    Image(systemName: "person.crop.circle")
                                        .foregroundStyle(.secondary)
                                    VStack(alignment: .leading) {
                                        Text(cred.userDisplayName ?? cred.userName ?? "(unnamed)")
                                            .font(.footnote)
                                        if let name = cred.userName, name != cred.userDisplayName {
                                            Text(name).font(.caption2).foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                .padding(.leading, 4)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 4)
                        Divider()
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label("Credential Inventory", systemImage: "list.bullet.rectangle")
        }
    }

    private func counter(_ title: String, _ value: Int) -> some View {
        VStack(alignment: .leading) {
            Text("\(value)").font(.title2).bold().monospacedDigit()
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
