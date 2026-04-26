import SwiftUI

struct LoginView: View {
    @Environment(AuthController.self) private var authController

    @State private var isSignUp = false
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isLoading = false
    @State private var showConfirmationAlert = false

    private let brandGreen = NeighborlyTheme.green
    private let lightGreen = NeighborlyTheme.greenSoft
    private let backgroundColor = NeighborlyTheme.background
    private let borderColor = NeighborlyTheme.border

    var body: some View {
        ZStack {
            backgroundColor
                .ignoresSafeArea()

            VStack {
                Spacer()

                VStack(spacing: 24) {
                    headerSection
                    modeToggle
                    formSection

                    if let error = authController.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 4)
                    }

                    submitButton
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 34)
                .frame(maxWidth: 620)
                .background(
                    RoundedRectangle(cornerRadius: 34, style: .continuous)
                        .fill(NeighborlyTheme.surface)
                        .shadow(color: .black.opacity(0.12), radius: 20, x: 0, y: 10)
                )
                .padding(.horizontal, 24)

                Spacer()
            }
        }
        .alert("Check Your Email", isPresented: $showConfirmationAlert) {
            Button("OK") {
                isSignUp = false
                clearForm()
            }
        } message: {
            Text("We sent a confirmation link to \(email). Please verify your email before signing in.")
        }
    }

    private var headerSection: some View {
        VStack(spacing: 8) {
            Text("Neighborly")
                .font(.system(size: 30, weight: .bold))
                .foregroundStyle(brandGreen)

            Text("Smart grocery trips, made easy.")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(NeighborlyTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
    }

    private var modeToggle: some View {
        HStack(spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isSignUp = false
                }
            } label: {
                HStack(spacing: 8) {
                    if !isSignUp {
                        Image(systemName: "checkmark")
                            .font(.system(size: 15, weight: .bold))
                    }

                    Text("Log In")
                        .font(.system(size: 17, weight: .semibold))
                }
                .foregroundStyle(!isSignUp ? .white : brandGreen)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(!isSignUp ? brandGreen : lightGreen)
            }

            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isSignUp = true
                }
            } label: {
                Text("Sign Up")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(isSignUp ? .white : brandGreen)
                    .frame(maxWidth: .infinity)
                    .frame(height: 58)
                    .background(isSignUp ? brandGreen : lightGreen)
            }
        }
        .clipShape(Capsule())
        .overlay(
            Capsule()
                .stroke(borderColor, lineWidth: 1.8)
        )
    }

    private var formSection: some View {
        VStack(spacing: 18) {
            if isSignUp {
                HStack(spacing: 14) {
                    themedTextField(
                        title: "First Name",
                        text: $firstName,
                        contentType: .givenName
                    )

                    themedTextField(
                        title: "Last Name",
                        text: $lastName,
                        contentType: .familyName
                    )
                }
            }

            themedTextField(
                title: "Email",
                text: $email,
                keyboardType: .emailAddress,
                contentType: .emailAddress,
                autocapitalization: .never
            )

            themedSecureField(
                title: "Password",
                text: $password,
                contentType: isSignUp ? .newPassword : .password
            )

            if isSignUp {
                themedSecureField(
                    title: "Confirm Password",
                    text: $confirmPassword,
                    contentType: .newPassword
                )
            }
        }
    }

    private var submitButton: some View {
        Button(action: submit) {
            Group {
                if isLoading {
                    ProgressView()
                        .tint(.white)
                } else {
                    Text(isSignUp ? "Create Account" : "Log In")
                        .font(.system(size: 20, weight: .semibold))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 62)
            .background(isFormValid ? brandGreen : NeighborlyTheme.textMuted.opacity(0.5))
            .foregroundStyle(.white)
            .clipShape(Capsule())
        }
        .disabled(!isFormValid || isLoading)
        .padding(.top, 4)
    }

    private func themedTextField(
        title: String,
        text: Binding<String>,
        keyboardType: UIKeyboardType = .default,
        contentType: UITextContentType? = nil,
        autocapitalization: TextInputAutocapitalization? = .sentences
    ) -> some View {
        TextField(title, text: text)
            .font(.system(size: 18))
            .foregroundStyle(NeighborlyTheme.textPrimary)
            .tint(brandGreen)
            .padding(.horizontal, 20)
            .frame(height: 74)
            .background(NeighborlyTheme.fieldBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(borderColor, lineWidth: 1.8)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .keyboardType(keyboardType)
            .textContentType(contentType)
            .textInputAutocapitalization(autocapitalization)
            .autocorrectionDisabled()
    }

    private func themedSecureField(
        title: String,
        text: Binding<String>,
        contentType: UITextContentType? = nil
    ) -> some View {
        SecureField(title, text: text)
            .font(.system(size: 18))
            .foregroundStyle(NeighborlyTheme.textPrimary)
            .tint(brandGreen)
            .padding(.horizontal, 20)
            .frame(height: 74)
            .background(NeighborlyTheme.fieldBackground)
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(borderColor, lineWidth: 1.8)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .textContentType(contentType)
    }

    private var isFormValid: Bool {
        let hasEmail = !email.isEmpty
        let hasPassword = !password.isEmpty

        if isSignUp {
            return hasEmail &&
                hasPassword &&
                !firstName.isEmpty &&
                !lastName.isEmpty &&
                password == confirmPassword &&
                password.count >= 6
        }

        return hasEmail && hasPassword
    }

    private func submit() {
        isLoading = true
        Task {
            defer { isLoading = false }

            if isSignUp {
                let signedIn = await authController.signUp(
                    email: email,
                    password: password,
                    firstName: firstName,
                    lastName: lastName
                )

                if !signedIn && authController.errorMessage == nil {
                    showConfirmationAlert = true
                }
            } else {
                await authController.signIn(email: email, password: password)
            }
        }
    }

    private func clearForm() {
        firstName = ""
        lastName = ""
        email = ""
        password = ""
        confirmPassword = ""
    }
}

#Preview {
    LoginView()
        .environment(AuthController())
}
