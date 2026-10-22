import { createClient } from '@supabase/supabase-js'
export const supabase = createClient(import.meta.env.VITE_SUPABASE_URL || '', import.meta.env.VITE_SUPABASE_ANON_KEY || '')
export const API = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export const money = (n: unknown) => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:0}).format(Number(n||0))
export const num = (v: unknown) => Number(v || 0)
export const today = () => new Date().toISOString().slice(0,10)
export async function api(resource:string, token:string, options:RequestInit={}) {
  const res = await fetch(`${API}/api/${resource}`,{...options,headers:{'Content-Type':'application/json','Authorization':`Bearer ${token}`,...options.headers}})
  if (!res.ok) { let msg='Request failed'; try { const j=await res.json(); msg=j.message||j.error||msg } catch {} throw new Error(msg) }
  return res.status===204 ? null : res.json()
}
