import SwiftUI
import PhotosUI
import Supabase

struct PriceReportSheet: View {
    let item: RouteItem
    let storeProductId: String
    let storeName: String
    let onSubmitted: (Double) -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var reportedPriceText: String = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var photoData: Data?
    @State private var isSubmitting: Bool = false
    @State private var errorMessage: String?

    private var parsedPrice: Double? {
        let cleaned = reportedPriceText.replacingOccurrences(of: "$", with: "")
                                       .trimmingCharacters(in: .whitespaces)
        guard let value = Double(cleaned), value > 0 else { return nil }
        return value
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Item") {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(NeighborlyTheme.textPrimary)
                            Text(storeName)
                                .font(.caption)
                                .foregroundStyle(NeighborlyTheme.textMuted)
                        }
                        Spacer()
                        Text((item.salePrice ?? item.price), format: .currency(code: "USD"))
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(NeighborlyTheme.green)
                            .strikethrough()
                    }
                }

                Section("Actual price you saw") {
                    HStack {
                        Text("$")
                            .foregroundStyle(NeighborlyTheme.textMuted)
                        TextField("0.00", text: $reportedPriceText)
                            .keyboardType(.decimalPad)
                    }
                }

                Section("Optional — photo of shelf tag") {
                    PhotosPicker(
                        selection: $photoItem,
                        matching: .images,
                        photoLibrary: .shared()
                    ) {
                        HStack {
                            Image(systemName: "camera")
                            Text(photoData == nil ? "Add photo" : "Photo selected")
                        }
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(.caption)
                            .foregroundStyle(NeighborlyTheme.orange)
                    }
                }
            }
            .navigationTitle("Report Price")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await submit() }
                    } label: {
                        if isSubmitting {
                            ProgressView()
                        } else {
                            Text("Submit").bold()
                        }
                    }
                    .disabled(parsedPrice == nil || isSubmitting)
                }
            }
            .onChange(of: photoItem) { _, newItem in
                guard let newItem else { photoData = nil; return }
                Task {
                    if let data = try? await newItem.loadTransferable(type: Data.self) {
                        photoData = data
                    }
                }
            }
        }
    }

    private func submit() async {
        guard let price = parsedPrice else { return }
        isSubmitting = true
        errorMessage = nil

        do {
            var photoPath: String?
            if let photoData {
                photoPath = try await uploadPhoto(data: photoData)
            }
            _ = try await APIService.submitPriceReport(
                storeProductId: storeProductId,
                reportedPrice:  price,
                photoPath:      photoPath
            )
            onSubmitted(price)
            dismiss()
        } catch let APIError.serverError(code) where code == 429 {
            errorMessage = "You've hit the daily report limit. Try again tomorrow."
        } catch {
            errorMessage = "Couldn't submit report. Try again."
        }
        isSubmitting = false
    }

    private func uploadPhoto(data: Data) async throws -> String {
        guard let userId = try? await supabase.auth.session.user.id.uuidString.lowercased() else {
            throw APIError.serverError(statusCode: 401)
        }
        let jpeg = compressJPEG(data: data, maxDimension: 2048, quality: 0.7) ?? data

        let path = "\(userId)/\(UUID().uuidString.lowercased()).jpg"
        try await supabase.storage
            .from("price-report-photos")
            .upload(path, data: jpeg, options: FileOptions(contentType: "image/jpeg"))
        return path
    }
}

private func compressJPEG(data: Data, maxDimension: CGFloat, quality: CGFloat) -> Data? {
    guard let image = UIImage(data: data) else { return nil }
    let size = image.size
    let longEdge = max(size.width, size.height)
    let scale = longEdge > maxDimension ? maxDimension / longEdge : 1
    let target = CGSize(width: size.width * scale, height: size.height * scale)

    let renderer = UIGraphicsImageRenderer(size: target)
    let resized = renderer.image { _ in
        image.draw(in: CGRect(origin: .zero, size: target))
    }
    return resized.jpegData(compressionQuality: quality)
}
