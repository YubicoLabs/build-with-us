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

/// Shows the outcome of attempting a `deleteCredential` with the read-only (PCMR) token.
/// A rejection is the expected, correct result — it proves the token cannot modify credentials.
struct ScopeProbeView: View {
    let result: ScopeProbeResult

    var body: some View {
        GroupBox {
            VStack(alignment: .leading, spacing: 8) {
                Label(headline, systemImage: icon)
                    .font(.subheadline).bold()
                    .foregroundStyle(tint)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Label("Read-Only Scope Probe", systemImage: "hand.raised")
        }
    }

    private var headline: String {
        switch result {
        case .rejected: return "Delete rejected — PCMR is read-only ✓"
        case .unexpectedlyAllowed: return "Delete was NOT rejected ⚠︎"
        case .notRun: return "Probe skipped"
        }
    }

    private var detail: String {
        switch result {
        case .rejected(let error):
            return "Attempting deleteCredential with the persistent token was rejected by the key: \(error)"
        case .unexpectedlyAllowed:
            return "The key did not reject the delete. This is unexpected for a PCMR token."
        case .notRun(let reason):
            return reason
        }
    }

    private var icon: String {
        switch result {
        case .rejected: return "checkmark.shield.fill"
        case .unexpectedlyAllowed: return "exclamationmark.shield.fill"
        case .notRun: return "shield.slash"
        }
    }

    private var tint: Color {
        switch result {
        case .rejected: return .green
        case .unexpectedlyAllowed: return .orange
        case .notRun: return .secondary
        }
    }
}
