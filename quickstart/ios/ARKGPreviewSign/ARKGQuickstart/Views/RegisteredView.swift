import SwiftUI

struct RegisteredView: View {
    let vm: ARKGViewModel

    var body: some View {
        VStack(spacing: 24) {
            #if os(iOS)
            Spacer().frame(height: 40)
            #endif

            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 60))
                .foregroundStyle(.green)

            VStack(spacing: 8) {
                Text("Step A complete")
                    .font(.title2.bold())
                Text("Your YubiKey can now be removed. Key derivation runs entirely offline.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
            }

            #if os(iOS)
            Spacer()
            #endif

            Button("Derive 5 Keys") {
                vm.deriveKeys()
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            #if os(iOS)
            .frame(maxWidth: .infinity)
            #endif
        }
        .padding()
        .navigationTitle("Registered")
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Start Over") { vm.reset() }
            }
        }
    }
}
