create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null default 'User',
  email text,
  created_at timestamptz not null default now()
);

create table if not exists public.investments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (length(trim(name)) > 0),
  type text not null check (type in ('Stocks','Mutual Funds','Fixed Deposit','Gold','Bonds','Other')),
  invested_amount numeric(14,2) not null check (invested_amount > 0),
  current_value numeric(14,2) not null check (current_value >= 0),
  return_percentage numeric(9,2) generated always as
    (case when invested_amount > 0 then ((current_value - invested_amount) / invested_amount) * 100 else 0 end) stored,
  risk_level text not null check (risk_level in ('Low','Medium','High')),
  investment_date date not null,
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  transaction_type text not null check (transaction_type in ('Income','Expense','Investment')),
  category text not null,
  amount numeric(14,2) not null check (amount > 0),
  transaction_date date not null,
  description text not null default '',
  created_at timestamptz not null default now()
);

create table if not exists public.financial_goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  goal_name text not null check (length(trim(goal_name)) > 0),
  target_amount numeric(14,2) not null check (target_amount > 0),
  current_amount numeric(14,2) not null default 0 check (current_amount >= 0),
  deadline date,
  importance text not null check (importance in ('Low','Medium','High')),
  status text not null check (status in ('Active','Completed','Paused')),
  description text,
  created_at timestamptz not null default now(),
  check (current_amount <= target_amount or status = 'Completed')
);

create table if not exists public.expense_categories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  category_name text not null,
  created_at timestamptz not null default now(),
  unique(user_id, category_name)
);

create table if not exists public.liabilities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  amount numeric(14,2) not null check (amount >= 0),
  type text not null check (type in ('Personal Loan','Education Loan','Car Loan','Credit Card','Other')),
  created_at timestamptz not null default now()
);

create table if not exists public.assets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  amount numeric(14,2) not null check (amount >= 0),
  type text not null check (type in ('Bank/Savings Balance','Other Assets')),
  created_at timestamptz not null default now()
);

create index if not exists investments_user_date_idx on public.investments(user_id, investment_date desc);
create index if not exists transactions_user_date_idx on public.transactions(user_id, transaction_date desc);
create index if not exists transactions_user_type_idx on public.transactions(user_id, transaction_type);
create index if not exists goals_user_status_idx on public.financial_goals(user_id, status);
create index if not exists liabilities_user_idx on public.liabilities(user_id);
create index if not exists assets_user_idx on public.assets(user_id);

-- Apply ownership isolation to every user-specific table.
do $$
declare t text;
begin
  foreach t in array array['profiles','investments','transactions','financial_goals','expense_categories','liabilities','assets']
  loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists own_rows_select on public.%I', t);
    execute format('drop policy if exists own_rows_insert on public.%I', t);
    execute format('drop policy if exists own_rows_update on public.%I', t);
    execute format('drop policy if exists own_rows_delete on public.%I', t);
    if t = 'profiles' then
      execute format('create policy own_rows_select on public.%I for select using (auth.uid() = id)', t);
      execute format('create policy own_rows_insert on public.%I for insert with check (auth.uid() = id)', t);
      execute format('create policy own_rows_update on public.%I for update using (auth.uid() = id) with check (auth.uid() = id)', t);
      execute format('create policy own_rows_delete on public.%I for delete using (auth.uid() = id)', t);
    else
      execute format('create policy own_rows_select on public.%I for select using (auth.uid() = user_id)', t);
      execute format('create policy own_rows_insert on public.%I for insert with check (auth.uid() = user_id)', t);
      execute format('create policy own_rows_update on public.%I for update using (auth.uid() = user_id) with check (auth.uid() = user_id)', t);
      execute format('create policy own_rows_delete on public.%I for delete using (auth.uid() = user_id)', t);
    end if;
  end loop;
end $$;

-- Automatically create a profile after Supabase Auth sign-up.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  insert into public.profiles(id, name, email)
  values (new.id, coalesce(new.raw_user_meta_data->>'name', split_part(new.email, '@', 1)), new.email)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();
