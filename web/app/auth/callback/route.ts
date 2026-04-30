import { type NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url)
  const code = searchParams.get('code')
  const rawNext = searchParams.get('next') ?? '/'
  const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/'

  if (!code) {
    return NextResponse.redirect(
      new URL(
        '/auth/error?reason=' +
          encodeURIComponent('Missing authorization code'),
        request.url
      )
    )
  }

  const supabase = await createClient()
  const { error } = await supabase.auth.exchangeCodeForSession(code)

  if (error) {
    return NextResponse.redirect(
      new URL(
        '/auth/error?reason=' + encodeURIComponent(error.message),
        request.url
      )
    )
  }

  return NextResponse.redirect(new URL(next, request.url))
}
