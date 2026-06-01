import SwiftUI

struct SignatureResultView: View {
    let vm: ARKGViewModel
    let message: Data
    let signature: Data
    let verified: Bool

    var body: some View {
        #if os(iOS)
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    verificationBadge
                    fields.padding(.horizontal)
                }
                .padding(.vertical)
            }

            startOverButton
                .padding()
                .background(.bar)
        }
        .navigationTitle("Result")
        .navigationBarBackButtonHidden(true)
        #else
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                verificationBadge
                fields.padding(.horizontal)
                startOverButton.padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Result")
        .navigationBarBackButtonHidden(true)
        #endif
    }

    private var fields: some View {
        VStack(alignment: .leading, spacing: 20) {
            field(label: "Message", value: String(data: message, encoding: .utf8) ?? hex(message))
            field(label: "Signature (\(signature.count) bytes)", value: hex(signature))
        }
    }

    private var startOverButton: some View {
        Button("Start Over") { vm.reset() }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .frame(maxWidth: .infinity)
    }

    private var verificationBadge: some View {
        HStack(spacing: 12) {
            Image(systemName: verified ? "checkmark.seal.fill" : "xmark.seal.fill")
                .font(.system(size: 40))
                .foregroundStyle(verified ? .green : .red)
            VStack(alignment: .leading) {
                Text(verified ? "Signature Valid" : "Signature Invalid")
                    .font(.title2.bold())
                Text(verified ? "Offline ECDSA-P256 verification passed." : "Verification failed.")
                    .foregroundStyle(.secondary)
                    .font(.subheadline)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal)
    }

    private func field(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value)
                .font(.body.monospaced())
                .textSelection(.enabled)
                .lineLimit(4)
        }
    }

    private func hex(_ d: Data) -> String {
        d.map { String(format: "%02x", $0) }.joined()
    }
}
