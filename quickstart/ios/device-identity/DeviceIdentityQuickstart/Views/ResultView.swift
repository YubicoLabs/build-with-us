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

/// Composes the full device-identity report after a successful run.
struct ResultView: View {
    let report: DeviceIdentityReport
    @ObservedObject var viewModel: DeviceIdentityViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                DeviceInfoView(report: report)
                CredentialListView(report: report)
                ScopeProbeView(result: report.scopeProbe)

                Button {
                    viewModel.reset()
                } label: {
                    Label("Done", systemImage: "chevron.left")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
            }
            .padding()
        }
    }
}
