'use server'

import { createClient } from '@/lib/supabase/server'

const SALE_PRICE_ERROR = 'Sale price must be lower than the regular price.'

type SupabaseClient = Awaited<ReturnType<typeof createClient>>

async function recordPriceHistory(
  supabase: SupabaseClient,
  rows: Array<{ storeProductId: string; price: number; salePrice: number | null; recordedAt: string }>
) {
  if (rows.length === 0) return null

  const { error } = await supabase
    .from('store_product_price_history')
    .insert(rows.map((row) => ({
      store_product_id: row.storeProductId,
      price: row.price,
      sale_price: row.salePrice,
      recorded_at: row.recordedAt,
    })))

  return error?.message ?? null
}

async function requireVendor() {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return { error: 'Not authenticated' as const, supabase: null, storeId: null }

  const { data: vendor } = await supabase
    .from('vendors')
    .select('store_id')
    .eq('user_id', user.id)
    .single()

  if (!vendor) return { error: 'No store assigned' as const, supabase: null, storeId: null }
  return { error: null, supabase, storeId: vendor.store_id as string }
}

export async function getVendorStore() {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error', data: null }

  const { data, error: queryError } = await supabase
    .from('stores')
    .select('id, name, chain, address, zip_code, lat, lng, phone, website_url')
    .eq('id', storeId)
    .single()

  if (queryError) return { error: queryError.message, data: null }
  return { data }
}

export async function getStoreProducts() {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error', data: [] }

  // TODO: move to proper server-side pagination (separate count + page queries,
  // server-side search, dedicated export endpoint). For now we hard-cap the
  // total rows so a vendor with a huge catalog can't make this loop run away.
  const PAGE_SIZE = 1000
  const MAX_ROWS = 5000
  const allRows: unknown[] = []
  let from = 0

  while (from < MAX_ROWS) {
    const remaining = MAX_ROWS - from
    const pageSize = Math.min(PAGE_SIZE, remaining)
    const { data, error: queryError } = await supabase
      .from('store_products')
      .select(`
        id, price, sale_price, in_stock, data_source, updated_at,
        products (
          id, name, brand, unit_size,
          product_categories (name)
        )
      `)
      .eq('store_id', storeId)
      .order('updated_at', { ascending: false })
      .order('id', { ascending: false })
      .range(from, from + pageSize - 1)

    if (queryError) return { error: queryError.message, data: [] }
    if (!data || data.length === 0) break

    allRows.push(...data)
    if (data.length < pageSize) break
    from += pageSize
  }

  return { data: allRows }
}

export async function getProductPriceHistory(storeProductId: string) {
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error', data: [] }

  const { data, error: queryError } = await supabase
    .from('store_product_price_history')
    .select('price, sale_price, created_at:recorded_at')
    .eq('store_product_id', storeProductId)
    .order('recorded_at', { ascending: true })

  if (queryError) return { error: queryError.message, data: [] }
  return { data: data || [] }
}

export async function getStoreReviews() {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error', data: [] }

  const { data, error: queryError } = await supabase
    .from('store_reviews')
    .select(`
      id, rating, comment, created_at,
      users (first_name, last_name)
    `)
    .eq('store_id', storeId)
    .order('created_at', { ascending: false })

  if (queryError) return { error: queryError.message, data: [] }
  return { data: data || [] }
}

export async function searchCatalog(query: string) {
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error', data: [] }

  const { data, error: queryError } = await supabase
    .from('products')
    .select(`
      id, name, brand, unit_size,
      product_categories (name)
    `)
    .ilike('name', `%${query}%`)
    .limit(10)

  if (queryError) return { error: queryError.message, data: [] }
  return { data: data || [] }
}

export async function updateProductPrice(storeProductId: string, price: number, salePrice: number | null) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const pricingError = validateProductPricing(price, salePrice)
  if (pricingError) return { error: pricingError }

  const now = new Date().toISOString()
  const { data: updatedRows, error: updateError } = await supabase
    .from('store_products')
    .update({ price, sale_price: salePrice, data_source: 'vendor', updated_at: now })
    .eq('id', storeProductId)
    .eq('store_id', storeId)
    .select('id, price, sale_price, updated_at')

  if (updateError) return { error: updateError.message }
  if (!updatedRows || updatedRows.length === 0) {
    return { error: 'Product not found or not owned by this store.' }
  }

  const { data: historyRows, error: historyError } = await supabase
    .from('store_product_price_history')
    .insert({
      store_product_id: storeProductId,
      price,
      sale_price: salePrice,
      recorded_at: now,
    })
    .select('price, sale_price, created_at:recorded_at')

  if (historyError) {
    return { error: `Price was updated, but history could not be saved: ${historyError.message}` }
  }

  return {
    success: true,
    data: updatedRows[0],
    history: historyRows?.[0] ?? { price, sale_price: salePrice, created_at: now },
  }
}

export async function toggleProductStock(storeProductId: string, inStock: boolean) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const { data: updatedRows, error: updateError } = await supabase
    .from('store_products')
    .update({ in_stock: inStock, data_source: 'vendor', updated_at: new Date().toISOString() })
    .eq('id', storeProductId)
    .eq('store_id', storeId)
    .select('id')

  if (updateError) return { error: updateError.message }
  if (!updatedRows || updatedRows.length === 0) {
    return { error: 'Product not found or not owned by this store.' }
  }
  return { success: true }
}

export async function deleteStoreProduct(storeProductId: string) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const { error: deleteError } = await supabase
    .from('store_products')
    .delete()
    .eq('id', storeProductId)
    .eq('store_id', storeId)

  if (deleteError) return { error: deleteError.message }
  return { success: true }
}

export async function addCatalogProduct(
  productId: string,
  price: number,
  salePrice: number | null = null,
  inStock: boolean = true,
) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const pricingError = validateProductPricing(price, salePrice)
  if (pricingError) return { error: pricingError }

  const now = new Date().toISOString()
  const { data: insertedRows, error: insertError } = await supabase
    .from('store_products')
    .insert({
      store_id: storeId,
      product_id: productId,
      price,
      sale_price: salePrice,
      in_stock: inStock,
      data_source: 'vendor',
      updated_at: now,
    })
    .select('id')

  if (insertError) {
    if (insertError.code === '23505') {
      return { error: 'This product is already in your store.' }
    }
    return { error: insertError.message }
  }
  const inserted = insertedRows?.[0]
  if (!inserted) return { error: 'Product was not added.' }

  const historyError = await recordPriceHistory(supabase, [{
    storeProductId: inserted.id,
    price,
    salePrice,
    recordedAt: now,
  }])
  if (historyError) return { error: `Product was added, but history could not be saved: ${historyError}` }

  return { success: true }
}

export async function createAndAddProduct(data: {
  name: string
  brand: string
  category: string
  unitSize: string
  price: number
  salePrice?: number | null
  inStock?: boolean
}) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const pricingError = validateProductPricing(data.price, data.salePrice ?? null)
  if (pricingError) return { error: pricingError }

  // Find or create the category
  let categoryId: string
  const { data: existingCat } = await supabase
    .from('product_categories')
    .select('id')
    .ilike('name', data.category)
    .single()

  if (existingCat) {
    categoryId = existingCat.id
  } else {
    const { data: newCat, error: catError } = await supabase
      .from('product_categories')
      .insert({ name: data.category, slug: data.category.toLowerCase().replace(/\s+/g, '-') })
      .select('id')
      .single()

    if (catError || !newCat) return { error: catError?.message || 'Failed to create category' }
    categoryId = newCat.id
  }

  // Create the product
  const { data: product, error: productError } = await supabase
    .from('products')
    .insert({
      name: data.name,
      brand: data.brand || null,
      unit_size: data.unitSize || null,
      category_id: categoryId,
    })
    .select('id')
    .single()

  if (productError || !product) return { error: productError?.message || 'Failed to create product' }

  // Add to store
  const now = new Date().toISOString()
  const { data: storeProductRows, error: storeProductError } = await supabase
    .from('store_products')
    .insert({
      store_id: storeId,
      product_id: product.id,
      price: data.price,
      sale_price: data.salePrice ?? null,
      in_stock: data.inStock ?? true,
      data_source: 'vendor',
      updated_at: now,
    })
    .select('id')

  if (storeProductError) return { error: storeProductError.message }
  const storeProduct = storeProductRows?.[0]
  if (!storeProduct) return { error: 'Product was created, but could not be added to your store.' }

  const historyError = await recordPriceHistory(supabase, [{
    storeProductId: storeProduct.id,
    price: data.price,
    salePrice: data.salePrice ?? null,
    recordedAt: now,
  }])
  if (historyError) return { error: `Product was added, but history could not be saved: ${historyError}` }

  return { success: true }
}

export async function updateVendorStoreInfo(data: {
  name: string
  chain?: string | null
  address?: string | null
  zipCode?: string | null
  phone?: string | null
  websiteUrl?: string | null
}) {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error' }

  const name = data.name.trim()
  if (!name) return { error: 'Store name is required.' }

  const { data: updatedRows, error: updateError } = await supabase
    .from('stores')
    .update({
      name,
      chain: data.chain?.trim() || null,
      address: data.address?.trim() || null,
      zip_code: data.zipCode?.trim() || null,
      phone: data.phone?.trim() || null,
      website_url: data.websiteUrl?.trim() || null,
    })
    .eq('id', storeId)
    .select('id, name, chain, address, zip_code, lat, lng, phone, website_url')
  if (updateError) return { error: updateError.message }

  if (!updatedRows || updatedRows.length === 0) {
    return { error: 'Store update did not affect any rows. This is usually an RLS/policy issue.' }
  }

  return { success: true, data: updatedRows[0] }
}

type ImportMatchInput = {
  name: string
  brand: string
  unitSize: string
  catalogProductId: string
}

type ImportMatchResult = {
  matchedProductId: string | null
  storeProductId: string | null
  currentPrice: number | null
  currentSalePrice: number | null
  currentInStock: boolean | null
}

type BulkImportPayload = {
  updates: Array<{ rowIndex: number; storeProductId: string; price: number; salePrice: number | null; inStock: boolean }>
  adds: Array<{ rowIndex: number; catalogProductId: string; price: number; salePrice: number | null; inStock: boolean }>
  creates: Array<{
    rowIndex: number
    name: string
    brand: string
    category: string
    unitSize: string
    price: number
    salePrice: number | null
    inStock: boolean
  }>
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function normalizeImportText(value: string | null | undefined) {
  return (value ?? '').trim().toLowerCase()
}

function isPositiveNumber(value: number) {
  return Number.isFinite(value) && value > 0
}

function validateProductPricing(price: number, salePrice: number | null) {
  if (!isPositiveNumber(price)) return 'Price must be greater than 0.'
  if (salePrice === null) return null
  if (!isPositiveNumber(salePrice)) return 'Sale price must be greater than 0.'
  if (salePrice >= price) return SALE_PRICE_ERROR
  return null
}

async function fetchAllRows<T>(
  queryPage: (from: number, to: number) => Promise<{ data: T[] | null; error: { message: string } | null }>,
  maxRows = 50000
) {
  const pageSize = 1000
  const rows: T[] = []

  for (let from = 0; from < maxRows; from += pageSize) {
    const { data, error } = await queryPage(from, from + pageSize - 1)
    if (error) return { data: null, error }
    if (!data || data.length === 0) break

    rows.push(...data)
    if (data.length < pageSize) break
  }

  return { data: rows, error: null }
}

export async function getProductCategories() {
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error', data: [] }

  const { data, error: queryError } = await fetchAllRows<{ name: string }>(async (from, to) =>
    await supabase
      .from('product_categories')
      .select('name')
      .order('name', { ascending: true })
      .range(from, to)
  )

  if (queryError) return { error: queryError.message, data: [] }
  return { data: (data ?? []).map((category) => category.name).filter(Boolean) as string[] }
}

export async function matchCatalogProducts(rows: ImportMatchInput[]): Promise<{
  error: string | null
  data: ImportMatchResult[] | null
}> {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error', data: null }

  const { data: storeInventory, error: inventoryError } = await fetchAllRows<{
    id: string
    product_id: string
    price: number
    sale_price: number | null
    in_stock: boolean
  }>(async (from, to) =>
    await supabase
      .from('store_products')
      .select('id, product_id, price, sale_price, in_stock')
      .eq('store_id', storeId)
      .order('id', { ascending: true })
      .range(from, to)
  )

  if (inventoryError) return { error: inventoryError.message, data: null }

  const inventoryByProductId = new Map(
    (storeInventory ?? []).map((storeProduct) => [String(storeProduct.product_id), storeProduct])
  )

  const lookupIds = [...new Set(
    rows
      .map((row) => row.catalogProductId.trim())
      .filter((id) => id && UUID_RE.test(id))
  )]

  const byId = new Map<string, { id: string; name: string; brand: string | null; unit_size: string | null }>()
  if (lookupIds.length > 0) {
    const { data: idMatches, error: idError } = await supabase
      .from('products')
      .select('id, name, brand, unit_size')
      .in('id', lookupIds)

    if (idError) return { error: idError.message, data: null }
    for (const product of idMatches ?? []) byId.set(product.id, product)
  }

  const nameRows = rows.filter((row) => !row.catalogProductId || !byId.has(row.catalogProductId))
  const uniqueNames = [...new Set(nameRows.map((row) => row.name.trim()).filter(Boolean))]
  const byNameBrandUnit: Array<{ id: string; name: string; brand: string | null; unit_size: string | null }> = []

  if (uniqueNames.length > 0) {
    // Avoid PostgREST OR filters here: product names commonly include spaces,
    // punctuation, and units that can produce malformed filter strings.
    const targetNames = new Set(uniqueNames.map(normalizeImportText))
    const pageSize = 1000
    const maxCatalogRows = 20000
    let from = 0

    while (from < maxCatalogRows) {
      const { data: nameMatches, error: nameError } = await supabase
        .from('products')
        .select('id, name, brand, unit_size')
        .order('id', { ascending: true })
        .range(from, from + pageSize - 1)

      if (nameError) return { error: nameError.message, data: null }
      if (!nameMatches || nameMatches.length === 0) break

      byNameBrandUnit.push(...nameMatches.filter((product) => targetNames.has(normalizeImportText(product.name))))

      if (nameMatches.length < pageSize) break
      from += pageSize
    }
  }

  const data = rows.map((row) => {
    const idMatch = row.catalogProductId ? byId.get(row.catalogProductId) : null
    const catalogMatch = idMatch ?? byNameBrandUnit.find((product) =>
      normalizeImportText(product.name) === normalizeImportText(row.name) &&
      normalizeImportText(product.brand) === normalizeImportText(row.brand) &&
      normalizeImportText(product.unit_size) === normalizeImportText(row.unitSize)
    ) ?? null

    if (!catalogMatch) {
      return {
        matchedProductId: null,
        storeProductId: null,
        currentPrice: null,
        currentSalePrice: null,
        currentInStock: null,
      }
    }

    const storeProduct = inventoryByProductId.get(catalogMatch.id)
    return {
      matchedProductId: catalogMatch.id,
      storeProductId: storeProduct?.id ?? null,
      currentPrice: storeProduct?.price ?? null,
      currentSalePrice: storeProduct?.sale_price ?? null,
      currentInStock: storeProduct?.in_stock ?? null,
    }
  })

  return { error: null, data }
}

export async function bulkImportProducts(payload: BulkImportPayload): Promise<{
  error: string | null
  data: { updated: number; added: number; created: number; failed: Array<{ rowIndex: number; reason: string }> } | null
}> {
  const { error, supabase, storeId } = await requireVendor()
  if (error || !supabase || !storeId) return { error: error || 'Unknown error', data: null }

  const now = new Date().toISOString()
  const failed: Array<{ rowIndex: number; reason: string }> = []
  let updated = 0
  let added = 0
  let created = 0

  await Promise.all(
    payload.updates.map(async (row) => {
      const pricingError = validateProductPricing(row.price, row.salePrice)
      if (!UUID_RE.test(row.storeProductId) || pricingError) {
        failed.push({ rowIndex: row.rowIndex, reason: pricingError || 'Invalid update payload' })
        return
      }

      const { data: updatedRows, error: updateError } = await supabase
        .from('store_products')
        .update({
          price: row.price,
          sale_price: row.salePrice,
          in_stock: row.inStock,
          data_source: 'vendor',
          updated_at: now,
        })
        .eq('id', row.storeProductId)
        .eq('store_id', storeId)
        .select('id')

      if (updateError) {
        failed.push({ rowIndex: row.rowIndex, reason: updateError.message })
      } else if (!updatedRows || updatedRows.length === 0) {
        failed.push({ rowIndex: row.rowIndex, reason: 'Product not found or not owned by this store.' })
      } else {
        const historyError = await recordPriceHistory(supabase, [{
          storeProductId: row.storeProductId,
          price: row.price,
          salePrice: row.salePrice,
          recordedAt: now,
        }])
        if (historyError) {
          failed.push({ rowIndex: row.rowIndex, reason: `Price updated, but history could not be saved: ${historyError}` })
        }
        updated++
      }
    })
  )

  if (payload.adds.length > 0) {
    const validAdds = payload.adds.filter((row) => {
      const pricingError = validateProductPricing(row.price, row.salePrice)
      const valid = UUID_RE.test(row.catalogProductId) && !pricingError
      if (!valid) failed.push({ rowIndex: row.rowIndex, reason: pricingError || 'Invalid add payload' })
      return valid
    })

    if (validAdds.length > 0) {
      const { data: inserted, error: insertError } = await supabase
        .from('store_products')
        .insert(validAdds.map((row) => ({
          store_id: storeId,
          product_id: row.catalogProductId,
          price: row.price,
          sale_price: row.salePrice,
          in_stock: row.inStock,
          data_source: 'vendor',
          updated_at: now,
        })))
        .select('id, price, sale_price')

      if (insertError) {
        validAdds.forEach((row) => failed.push({ rowIndex: row.rowIndex, reason: insertError.message }))
      } else {
        added += inserted?.length ?? 0
        const historyError = await recordPriceHistory(
          supabase,
          (inserted ?? []).map((storeProduct) => ({
            storeProductId: storeProduct.id,
            price: Number(storeProduct.price),
            salePrice: storeProduct.sale_price === null ? null : Number(storeProduct.sale_price),
            recordedAt: now,
          }))
        )
        if (historyError) {
          validAdds.forEach((row) =>
            failed.push({ rowIndex: row.rowIndex, reason: `Product added, but history could not be saved: ${historyError}` })
          )
        }
      }
    }
  }

  for (const row of payload.creates) {
    const name = row.name.trim()
    const category = row.category.trim()
    const pricingError = validateProductPricing(row.price, row.salePrice)

    if (!name || !category || pricingError) {
      failed.push({ rowIndex: row.rowIndex, reason: pricingError || 'Invalid create payload' })
      continue
    }

    let categoryId: string
    const { data: existingCategory, error: existingCategoryError } = await supabase
      .from('product_categories')
      .select('id')
      .ilike('name', category)
      .maybeSingle()

    if (existingCategoryError) {
      failed.push({ rowIndex: row.rowIndex, reason: existingCategoryError.message })
      continue
    }

    if (existingCategory) {
      categoryId = existingCategory.id
    } else {
      const { data: newCategory, error: categoryError } = await supabase
        .from('product_categories')
        .insert({ name: category, slug: category.toLowerCase().replace(/\s+/g, '-') })
        .select('id')
        .single()

      if (categoryError || !newCategory) {
        failed.push({ rowIndex: row.rowIndex, reason: categoryError?.message || 'Failed to create category' })
        continue
      }
      categoryId = newCategory.id
    }

    const { data: product, error: productError } = await supabase
      .from('products')
      .insert({
        name,
        brand: row.brand.trim() || null,
        unit_size: row.unitSize.trim() || null,
        category_id: categoryId,
      })
      .select('id')
      .single()

    if (productError || !product) {
      failed.push({ rowIndex: row.rowIndex, reason: productError?.message || 'Failed to create product' })
      continue
    }

    const { data: storeProductRows, error: storeProductError } = await supabase
      .from('store_products')
      .insert({
        store_id: storeId,
        product_id: product.id,
        price: row.price,
        sale_price: row.salePrice,
        in_stock: row.inStock,
        data_source: 'vendor',
        updated_at: now,
      })
      .select('id')

    if (storeProductError) {
      failed.push({ rowIndex: row.rowIndex, reason: storeProductError.message })
    } else {
      const storeProduct = storeProductRows?.[0]
      if (!storeProduct) {
        failed.push({ rowIndex: row.rowIndex, reason: 'Product was created, but could not be added to your store.' })
        continue
      }
      const historyError = await recordPriceHistory(supabase, [{
        storeProductId: storeProduct.id,
        price: row.price,
        salePrice: row.salePrice,
        recordedAt: now,
      }])
      if (historyError) {
        failed.push({ rowIndex: row.rowIndex, reason: `Product created, but history could not be saved: ${historyError}` })
      }
      created++
    }
  }

  return { error: null, data: { updated, added, created, failed } }
}
