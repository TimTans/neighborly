import { redirect } from 'next/navigation'
import React from 'react'

import { createAdminClient, createClient } from '@/lib/supabase/server'

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createClient()
  const {
    data: { user },
  } = await supabase.auth.getUser()

  if (!user) {
    redirect('/auth/login')
  }

  const adminClient = createAdminClient()
  const { data: profile } = await adminClient
    .from('users')
    .select('role')
    .eq('id', user.id)
    .single()

  if (profile?.role !== 'admin') {
    if (profile?.role === 'vendor') redirect('/vendordashboard')
    if (profile?.role === 'pending_vendor') redirect('/auth/pending-approval')
    redirect('/dashboard')
  }

  return <>{children}</>
}

