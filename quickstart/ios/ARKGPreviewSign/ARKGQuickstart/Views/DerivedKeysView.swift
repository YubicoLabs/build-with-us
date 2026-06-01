import SwiftUI

struct DerivedKeysView: View {
    let vm: ARKGViewModel
    let derivedKeys: [ARKGViewModel.DerivedKey]

    var body: some View {
        List(derivedKeys) { key in
            Button {
                vm.selectKey(key)
            } label: {
                KeyRow(key: key)
            }
            .buttonStyle(.plain)
        }
        .navigationTitle("Derived Keys (\(derivedKeys.count))")
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Start Over") { vm.reset() }
            }
        }
    }
}

private struct KeyRow: View {
    let key: ARKGViewModel.DerivedKey

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Key \(String(format: "%02d", key.id))")
                .font(.headline)
            Label(hex(key.publicKey), systemImage: "key")
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Label(hex(key.arkgKeyHandle), systemImage: "doc.badge.key")
                .font(.caption.monospaced())
                .foregroundStyle(.tertiary)
                .lineLimit(1)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
    }

    private func hex(_ d: Data) -> String {
        d.prefix(20).map { String(format: "%02x", $0) }.joined() + (d.count > 20 ? "…" : "")
    }
}
