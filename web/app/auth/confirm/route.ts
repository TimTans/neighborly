import { type NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url)
  const token_hash = searchParams.get('token_hash')
  const type = searchParams.get('type') as
    | 'signup'
    | 'email'
    | 'recovery'
    | 'invite'
    | null
  const rawNext = searchParams.get('next') ?? '/'
  const next = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/'

  if (!token_hash || !type) {
    return NextResponse.redirect(
      new URL(
        '/auth/error?reason=' +
          encodeURIComponent('Missing confirmation parameters'),
        request.url
      )
    )
  }

  const supabase = await createClient()
  const { error } = await supabase.auth.verifyOtp({ token_hash, type })

  if (error) {
    return NextResponse.redirect(
      new URL(
        '/auth/error?reason=' + encodeURIComponent(error.message),
        request.url
      )
    )
  }

  if (type === 'recovery') {
    return NextResponse.redirect(new URL('/auth/reset-password', request.url))
  }

  if (type === 'signup' || type === 'email') {
    return NextResponse.redirect(new URL('/auth/confirmed', request.url))
  }

  return NextResponse.redirect(new URL(next, request.url))
}
