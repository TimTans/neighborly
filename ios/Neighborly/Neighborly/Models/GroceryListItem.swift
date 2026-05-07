import Foundation
import SwiftData

@Model
final class GroceryListItem {
    var name: String
    var price: Double
    var unitSize: String
    var upc: String
    var quantity: Int
    var dateAdded: Date
    var productId: String?

    init(name: String, price: Double, unitSize: String, upc: String, quantity: Int = 1, productId: String? = nil) {
        self.name = name
        self.price = price
        self.unitSize = unitSize
        self.upc = upc
        self.quantity = quantity
        self.dateAdded = Date()
        self.productId = productId
    }

    convenience init(from product: Product) {
        self.init(
            name: product.name,
            price: product.bestPrice ?? 0,
            unitSize: product.unitSize,
            upc: product.upc ?? "",
            productId: product.id
        )
    }

    convenience init(from result: ProductSearchResult) {
        self.init(
            name: result.name,
            price: result.bestPrice ?? 0,
            unitSize: result.unitSize,
            upc: result.upc ?? "",
            productId: result.id
        )
    }
}
