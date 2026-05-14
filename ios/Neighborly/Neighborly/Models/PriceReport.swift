import Foundation

// payload sent to POST /price-reports
struct PriceReportSubmitRequest: Codable {
    let storeProductId: String
    let reportedPrice: Double
    let photoPath: String?

    enum CodingKeys: String, CodingKey {
        case storeProductId = "store_product_id"
        case reportedPrice  = "reported_price"
        case photoPath      = "photo_path"
    }
}

struct PriceReportSubmitResponse: Codable {
    let id: String
    let createdAt: String?
}

// payload sent to POST /price-reports/summary
struct PriceReportPair: Codable, Hashable {
    let productId: String
    let storeId: String

    enum CodingKeys: String, CodingKey {
        case productId = "product_id"
        case storeId   = "store_id"
    }
}

struct PriceReportSummary: Codable, Hashable, Identifiable {
    let productId: String
    let storeId: String
    let count: Int
    let latestReportedPrice: Double
    let latestReportedAt: String

    var id: String { "\(productId)|\(storeId)" }
}

struct PriceReportSummaryResponse: Codable {
    let summaries: [PriceReportSummary]
}

// in-memory key used by RouteView to look up summaries
struct PriceReportPairKey: Hashable {
    let productId: String
    let storeId: String
}
