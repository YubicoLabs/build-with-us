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

struct ContentView: View {
    @StateObject private var viewModel = DeviceIdentityViewModel()

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.phase {
                case .idle:
                    HomeView(viewModel: viewModel)
                case .working(let message):
                    InProgressView(message: message)
                case .unsupported(let message):
                    MessageView(
                        systemImage: "exclamationmark.triangle",
                        tint: .orange,
                        title: "Not Supported",
                        message: message,
                        actionTitle: "Back",
                        action: viewModel.reset
                    )
                case .failed(let message):
                    MessageView(
                        systemImage: "xmark.octagon",
                        tint: .red,
                        title: "Something Went Wrong",
                        message: message,
                        actionTitle: "Back",
                        action: viewModel.reset
                    )
                case .result(let report):
                    ResultView(report: report, viewModel: viewModel)
                }
            }
            .navigationTitle("Device Identity")
            .frame(minWidth: 420, minHeight: 560)
        }
    }
}

/// A generic centered message screen used for unsupported / error states.
struct MessageView: View {
    let systemImage: String
    let tint: Color
    let title: String
    let message: String
    let actionTitle: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: systemImage)
                .font(.system(size: 48))
                .foregroundStyle(tint)
            Text(title).font(.title2).bold()
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
