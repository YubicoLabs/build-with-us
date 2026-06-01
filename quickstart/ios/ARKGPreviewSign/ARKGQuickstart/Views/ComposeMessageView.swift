import SwiftUI

struct ComposeMessageView: View {
    let vm: ARKGViewModel
    let derivedKey: ARKGViewModel.DerivedKey

    @State private var message: String = "Hello World"

    var body: some View {
        #if os(iOS)
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    keySummary.padding(.horizontal)
                    messageField.padding(.horizontal)
                }
                .padding(.vertical)
            }
            .scrollDismissesKeyboard(.interactively)

            signButton
                .padding()
                .background(.bar)
        }
        .navigationTitle("Sign Message")
        .navigationBarBackButtonHidden(true)
        .toolbar { startOverToolbar }
        #else
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                keySummary.padding(.horizontal)
                messageField.padding(.horizontal)
                signButton.padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Sign Message")
        .navigationBarBackButtonHidden(true)
        .toolbar { startOverToolbar }
        #endif
    }

    private var messageField: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Message to sign")
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField("Message", text: $message, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(3...6)
                .font(.body.monospaced())
                .autocorrectionDisabled()
        }
    }

    private var signButton: some View {
        Button("Sign with YubiKey") {
            let bytes = Data(message.utf8)
            Task { await vm.sign(message: bytes) }
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .frame(maxWidth: .infinity)
        .disabled(message.isEmpty)
    }

    @ToolbarContentBuilder
    private var startOverToolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Start Over") { vm.reset() }
        }
    }

    private var keySummary: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Using Derived Key \(String(format: "%02d", derivedKey.id))")
                .font(.headline)
            Label(hex(derivedKey.publicKey), systemImage: "key")
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private func hex(_ d: Data) -> String {
        d.prefix(20).map { String(format: "%02x", $0) }.joined() + (d.count > 20 ? "…" : "")
    }
}
