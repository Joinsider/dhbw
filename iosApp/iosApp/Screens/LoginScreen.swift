// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// Dualis sign-in.
///
/// The validation happens in `AuthStore` and comes back as `UsernameError`/`PasswordError`, so
/// this screen only decides which sentence goes with which enum — the same two rules the Compose
/// form applies, without a second implementation of them.
struct LoginScreen: View {

    @Environment(AppModel.self) private var model
    @FocusState private var focused: Field?

    private enum Field { case username, password }

    private var state: AuthState { model.auth.state }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("login.email", text: usernameBinding, prompt: Text("login.emailPrompt"))
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($focused, equals: .username)
                        .submitLabel(.next)
                        .onSubmit { focused = .password }
                        .accessibilityIdentifier("loginUsernameField")
                    if let message = usernameMessage {
                        Text(message).font(.footnote).foregroundStyle(.red)
                    }

                    // .password rather than .newPassword: this is a sign-in, so iOS offers the
                    // saved credential instead of proposing a generated one.
                    SecureField("login.password", text: passwordBinding, prompt: Text("login.passwordPrompt"))
                        .textContentType(.password)
                        .focused($focused, equals: .password)
                        .submitLabel(.go)
                        .onSubmit { submit() }
                        .accessibilityIdentifier("loginPasswordField")
                    if let message = passwordMessage {
                        Text(message).font(.footnote).foregroundStyle(.red)
                    }
                } header: {
                    Text("login.header")
                } footer: {
                    if let error = state.loginError {
                        Text(error.userMessage)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("loginError")
                    }
                }

                Section {
                    Button(action: submit) {
                        HStack {
                            Spacer()
                            if state.isSubmitting {
                                ProgressView()
                            } else {
                                Text("login.submit")
                            }
                            Spacer()
                        }
                    }
                    .disabled(state.isSubmitting)
                    .accessibilityIdentifier("loginSubmitButton")
                }
            }
            .navigationTitle("app.name")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func submit() {
        focused = nil
        model.auth.dispatch(AuthIntentSubmitted())
    }

    /// The store owns the field contents, so the binding writes through it instead of keeping a
    /// second copy here — a `@State` string next to `AuthState.username` is exactly the kind of
    /// duplicated truth P4 removed on the other platforms.
    private var usernameBinding: Binding<String> {
        Binding(
            get: { state.username },
            set: { model.auth.dispatch(AuthIntentUsernameChanged(value: $0)) }
        )
    }

    private var passwordBinding: Binding<String> {
        Binding(
            get: { state.password },
            set: { model.auth.dispatch(AuthIntentPasswordChanged(value: $0)) }
        )
    }

    private var usernameMessage: String? {
        guard let error = state.usernameError else { return nil }
        return error == UsernameError.empty
            ? String(localized: "login.usernameEmpty")
            : String(localized: "login.usernameNotDhbw")
    }

    private var passwordMessage: String? {
        state.passwordError == nil ? nil : String(localized: "login.passwordEmpty")
    }
}
