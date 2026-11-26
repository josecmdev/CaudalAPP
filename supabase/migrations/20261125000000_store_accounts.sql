-- Cuentas pendientes por tienda (deudas, envases, entregas), una fila por tableta y tienda.
-- La tableta sube todas sus tiendas, también las que ya no deben nada (fila en 0), para que la página no muestre deudas ya pagadas.

create table public.store_accounts (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  store_name text not null,
  money_due integer not null default 0,
  paid integer not null default 0,
  pending_deliveries jsonb not null default '{}',
  pending_containers jsonb not null default '{}',
  updated_by uuid default auth.uid(), -- lo exige touch_updated_at
  updated_at timestamptz not null default now(),
  primary key (device_id, id)
);

create trigger store_accounts_touch
  before insert or update on public.store_accounts
  for each row execute function public.touch_updated_at();

-- Mismas reglas que las demás tablas por tableta: el vendedor ve y escribe lo suyo; el admin lee todo.
alter table public.store_accounts enable row level security;
create policy "leer propio o admin" on public.store_accounts for select using (user_id = auth.uid() or public.is_admin());
create policy "insertar propio" on public.store_accounts for insert with check (user_id = auth.uid());
create policy "editar propio" on public.store_accounts for update using (user_id = auth.uid()) with check (user_id = auth.uid());
