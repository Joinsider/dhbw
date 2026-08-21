// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// Dualis sign-in.
///
/// The validation happens in `AuthStore` and comes back as `UsernameError`/`PasswordError`, so
/// this screen only decides which sentence goes with which enum — the same two rules the Compose
/// form applies, without a second implementation of them.
///
/// Not a `Form`: this is the first screen anybody sees, and a grouped table with two rows in it
/// looks like a settings page for an app you have not opened yet. The controls are still the
/// stock `TextField`, `SecureField` and `Button` — only the frame around them is the app's own.
struct LoginScreen: View {

    @Environment(AppModel.self) private var model
    @FocusState private var focused: Field?

    private enum Field { case username, password }

    private var state: AuthState { model.auth.state }

    var body: some View {
        ScrollView {
            VStack(spacing: 28) {
                header
                card
                submitButton
                if let error = state.loginError {
                    Label(error.userMessage, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityIdentifier("loginError")
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, 48)
            .padding(.bottom, 32)
            .frame(maxWidth: 480)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .scrollDismissesKeyboard(.interactively)
    }

    private var header: some View {
        VStack(spacing: 16) {
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.brandGradient)
                .frame(width: 88, height: 88)
                .overlay {
                    Image(systemName: "graduationcap.fill")
                        .font(.system(size: 40, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .shadow(color: Color.brand.opacity(0.35), radius: 16, y: 8)
                .accessibilityHidden(true)

            VStack(spacing: 6) {
                Text("app.name")
                    .font(.largeTitle.bold())
                    .fontDesign(.rounded)
                Text("login.header")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var card: some View {
        VStack(spacing: 0) {
            field(
                icon: "envelope",
                title: "login.email",
                message: usernameMessage
            ) {
                TextField("login.email", text: usernameBinding, prompt: Text("login.emailPrompt"))
                    .textContentType(.username)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focused, equals: .username)
                    .submitLabel(.next)
                    .onSubmit { focused = .password }
                    .accessibilityIdentifier("loginUsernameField")
            }

            Divider().padding(.leading, 44)

            // .password rather than .newPassword: this is a sign-in, so iOS offers the saved
            // credential instead of proposing a generated one.
            field(
                icon: "lock",
                title: "login.password",
                message: passwordMessage
            ) {
                SecureField("login.password", text: passwordBinding, prompt: Text("login.passwordPrompt"))
                    .textContentType(.password)
                    .focused($focused, equals: .password)
                    .submitLabel(.go)
                    .onSubmit { submit() }
                    .accessibilityIdentifier("loginPasswordField")
            }
        }
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 18))
    }

    private func field<Content: View>(
        icon: String,
        title: LocalizedStringKey,
        message: String?,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .foregroundStyle(Color.brand)
                    .frame(width: 20)
                    .accessibilityHidden(true)
                content()
            }
            if let message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .padding(.leading, 32)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    private var submitButton: some View {
        Button(action: submit) {
            ZStack {
                // The label stays in the layout while the spinner shows, so the button does not
                // change size the moment it is pressed.
                Text("login.submit").opacity(state.isSubmitting ? 0 : 1)
                if state.isSubmitting {
                    ProgressView().tint(.white)
                }
            }
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(.brandGradient, in: .rect(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(state.isSubmitting)
        .shadow(color: Color.brand.opacity(0.3), radius: 12, y: 6)
        .accessibilityIdentifier("loginSubmitButton")
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
