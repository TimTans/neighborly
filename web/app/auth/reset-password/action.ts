'use server'

import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'

export async function updatePassword(formData: FormData) {
  const supabase = await createClient()

  const password = formData.get('password') as string
  const confirm_password = formData.get('confirm_password') as string

  if (!password || password.length < 8) {
    redirect(
      '/auth/reset-password?error=' +
        encodeURIComponent('Password must be at least 8 characters')
    )
  }

  if (password !== confirm_password) {
    redirect(
      '/auth/reset-password?error=' +
        encodeURIComponent('Passwords do not match')
    )
  }

  const { error } = await supabase.auth.updateUser({ password })

  if (error) {
    redirect(
      '/auth/reset-password?error=' + encodeURIComponent(error.message)
    )
  }

  redirect('/auth/reset-password?success=true')
}
