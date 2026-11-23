-- Caudal App: sincronización de tabletas y panel web.
-- Cada tableta sube sus datos con su propio device_id; la llave es (device_id, id) para que dos
-- tabletas nunca choquen aunque generen el mismo id local.

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null,
  role text not null default 'vendedor' check (role in ('admin', 'vendedor')),
  created_at timestamptz not null default now()
);

-- Cada usuario creado por el admin en Authentication recibe perfil de vendedor.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, username)
  values (new.id, split_part(new.email, '@', 1))
  on conflict (id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

create function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin');
$$;

create table public.devices (
  id text primary key,
  user_id uuid not null references auth.users (id) default auth.uid(),
  name text not null,
  last_latitude double precision,
  last_longitude double precision,
  last_position_at timestamptz,
  last_seen_at timestamptz not null default now()
);

create table public.positions (
  id bigint generated always as identity primary key,
  device_id text not null references public.devices (id) on delete cascade,
  user_id uuid not null references auth.users (id) default auth.uid(),
  route_id text,
  latitude double precision not null,
  longitude double precision not null,
  recorded_at timestamptz not null
);
create index positions_device_time on public.positions (device_id, recorded_at desc);

create table public.products (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  name text not null,
  suggested_price integer,
  returnable boolean not null default false,
  archived boolean not null default false,
  primary key (device_id, id)
);

create table public.stores (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  name text not null,
  latitude double precision not null,
  longitude double precision not null,
  phone text,
  archived boolean not null default false,
  primary key (device_id, id)
);

create table public.routes (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  name text not null,
  status text not null check (status in ('activa', 'finalizada')),
  started_at timestamptz not null,
  finished_at timestamptz,
  initial_cash integer not null default 0,
  total_sales integer not null default 0,
  cash_collected integer not null default 0,
  money_pending integer not null default 0,
  total_expenses integer not null default 0,
  expected_cash integer not null default 0,
  counted_cash integer,
  stock jsonb not null default '{}',
  counted_stock jsonb not null default '{}',
  archived boolean not null default false,
  primary key (device_id, id)
);

create table public.sale_lines (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  route_id text not null,
  sale_id text not null,
  store_id text,
  store_name text,
  product_id text not null,
  product_name text not null,
  requested integer not null,
  delivered integer not null,
  total integer not null,
  recorded_at timestamptz not null,
  cancelled boolean not null default false,
  latitude double precision,
  longitude double precision,
  primary key (device_id, id)
);
create index sale_lines_time on public.sale_lines (recorded_at);

-- Movimientos de ruta: cargas (RESTOCK), gastos, cobros, anulaciones, etc.
create table public.movements (
  device_id text not null references public.devices (id) on delete cascade,
  id text not null,
  user_id uuid not null references auth.users (id) default auth.uid(),
  route_id text not null,
  type text not null,
  title text not null,
  detail text not null default '',
  cash_delta integer not null default 0,
  recorded_at timestamptz not null,
  primary key (device_id, id)
);

-- RLS: el vendedor solo ve y escribe lo suyo; el admin lee todo.
alter table public.profiles enable row level security;
create policy "perfil propio o admin" on public.profiles for select
  using (id = auth.uid() or public.is_admin());

do $$
declare t text;
begin
  foreach t in array array['devices', 'positions', 'products', 'stores', 'routes', 'sale_lines', 'movements'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('create policy "leer propio o admin" on public.%I for select using (user_id = auth.uid() or public.is_admin())', t);
    execute format('create policy "insertar propio" on public.%I for insert with check (user_id = auth.uid())', t);
    execute format('create policy "editar propio" on public.%I for update using (user_id = auth.uid()) with check (user_id = auth.uid())', t);
  end loop;
end $$;

-- Ventas válidas con precio unitario y día de la semana (hora de Guatemala) para el panel.
create view public.sales_report with (security_invoker = true) as
select
  s.*,
  case when s.delivered > 0 then round(s.total::numeric / s.delivered, 2) end as unit_price,
  (s.recorded_at at time zone 'America/Guatemala')::date as sale_date,
  extract(isodow from s.recorded_at at time zone 'America/Guatemala')::int as weekday
from public.sale_lines s
where not s.cancelled;
