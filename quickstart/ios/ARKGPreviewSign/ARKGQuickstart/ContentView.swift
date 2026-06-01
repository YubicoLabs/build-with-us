import SwiftUI

struct ContentView: View {
    @StateObject private var vm = ARKGViewModel()

    var body: some View {
        NavigationStack {
            Group {
                switch vm.state {
                case .idle:
                    StepAView(vm: vm)
                case .inProgress:
                    InProgressView()
                case .registered:
                    RegisteredView(vm: vm)
                case .keysReady(_, _, let keys):
                    DerivedKeysView(vm: vm, derivedKeys: keys)
                case .composeMessage(_, _, let derivedKey):
                    ComposeMessageView(vm: vm, derivedKey: derivedKey)
                case .signed(let message, let signature, _):
                    SignatureView(vm: vm, message: message, signature: signature)
                case .verified(let message, let signature, let isValid):
                    SignatureResultView(vm: vm, message: message, signature: signature, verified: isValid)
                case .error(let msg):
                    ErrorView(vm: vm, message: msg)
                }
            }
            .navigationTitle("ARKG Quickstart")
        }
    }
}

// MARK: - Step A idle view

private struct StepAView: View {
    let vm: ARKGViewModel

    var body: some View {
        VStack(spacing: 24) {
            #if os(iOS)
            Spacer().frame(height: 40)
            #endif

            Image(systemName: "key.fill")
                .font(.system(size: 60))
                .foregroundStyle(.secondary)
            Text("Register your YubiKey to create an ARKG credential. You'll then derive offline keys, sign a message, and verify the signature.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)

            #if os(iOS)
            Spacer()
            #endif

            Button("Register YubiKey") {
                Task { await vm.register() }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            #if os(iOS)
            .frame(maxWidth: .infinity)
            #endif
        }
        .padding()
    }
}

// MARK: - Error view

private struct ErrorView: View {
    let vm: ARKGViewModel
    let message: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundStyle(.red)
            Text(message)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button("Start Over") { vm.reset() }
                .buttonStyle(.bordered)
        }
        .padding()
    }
}
