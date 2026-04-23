'use server'

import { createClient } from '@/lib/supabase/server'

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

  const PAGE_SIZE = 1000
  const allRows: unknown[] = []
  let from = 0

  while (true) {
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
      .range(from, from + PAGE_SIZE - 1)

    if (queryError) return { error: queryError.message, data: [] }
    if (!data || data.length === 0) break

    allRows.push(...data)
    if (data.length < PAGE_SIZE) break
    from += PAGE_SIZE
  }

  return { data: allRows }
}

export async function getProductPriceHistory(storeProductId: string) {
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error', data: [] }

  const { data, error: queryError } = await supabase
    .from('store_product_price_history')
    .select('price, sale_price, created_at')
    .eq('store_product_id', storeProductId)
    .order('created_at', { ascending: true })

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
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error' }

  const { error: updateError } = await supabase
    .from('store_products')
    .update({ price, sale_price: salePrice, data_source: 'vendor', updated_at: new Date().toISOString() })
    .eq('id', storeProductId)

  if (updateError) return { error: updateError.message }
  return { success: true }
}

export async function toggleProductStock(storeProductId: string, inStock: boolean) {
  const { error, supabase } = await requireVendor()
  if (error || !supabase) return { error: error || 'Unknown error' }

  const { error: updateError } = await supabase
    .from('store_products')
    .update({ in_stock: inStock, data_source: 'vendor', updated_at: new Date().toISOString() })
    .eq('id', storeProductId)

  if (updateError) return { error: updateError.message }
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

  const { error: insertError } = await supabase
    .from('store_products')
    .insert({
      store_id: storeId,
      product_id: productId,
      price,
      sale_price: salePrice,
      in_stock: inStock,
      data_source: 'vendor',
      updated_at: new Date().toISOString(),
    })

  if (insertError) {
    if (insertError.code === '23505') {
      return { error: 'This product is already in your store.' }
    }
    return { error: insertError.message }
  }
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
  const { error: storeProductError } = await supabase
    .from('store_products')
    .insert({
      store_id: storeId,
      product_id: product.id,
      price: data.price,
      sale_price: data.salePrice ?? null,
      in_stock: data.inStock ?? true,
      data_source: 'vendor',
      updated_at: new Date().toISOString(),
    })

  if (storeProductError) return { error: storeProductError.message }
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
