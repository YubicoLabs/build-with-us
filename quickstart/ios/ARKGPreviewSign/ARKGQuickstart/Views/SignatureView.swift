import SwiftUI

struct SignatureView: View {
    let vm: ARKGViewModel
    let message: Data
    let signature: Data

    var body: some View {
        #if os(iOS)
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header.padding(.horizontal)
                    fields.padding(.horizontal)
                }
                .padding(.vertical)
            }

            verifyButton
                .padding()
                .background(.bar)
        }
        .navigationTitle("Signature")
        .navigationBarBackButtonHidden(true)
        .toolbar { startOverToolbar }
        #else
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header.padding(.horizontal)
                fields.padding(.horizontal)
                verifyButton.padding(.horizontal)
            }
            .padding(.vertical)
        }
        .navigationTitle("Signature")
        .navigationBarBackButtonHidden(true)
        .toolbar { startOverToolbar }
        #endif
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "signature")
                .font(.system(size: 36))
                .foregroundStyle(.blue)
            VStack(alignment: .leading) {
                Text("Signature Created")
                    .font(.title2.bold())
                Text("Tap below to run offline ECDSA-P256 verification.")
                    .foregroundStyle(.secondary)
                    .font(.subheadline)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var fields: some View {
        VStack(alignment: .leading, spacing: 20) {
            field(label: "Message", value: String(data: message, encoding: .utf8) ?? hex(message))
            field(label: "Signature (\(signature.count) bytes, DER)", value: hex(signature))
        }
    }

    private var verifyButton: some View {
        Button("Verify Signature") {
            vm.verify()
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .frame(maxWidth: .infinity)
    }

    @ToolbarContentBuilder
    private var startOverToolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Start Over") { vm.reset() }
        }
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
