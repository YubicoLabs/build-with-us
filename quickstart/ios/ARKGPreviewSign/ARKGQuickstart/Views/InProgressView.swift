import SwiftUI

struct InProgressView: View {
    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
                .controlSize(.large)
            Text("Waiting for YubiKey…")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}
