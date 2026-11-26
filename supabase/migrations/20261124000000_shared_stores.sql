-- Tiendas compartidas por todas las tabletas. Cada tienda tiene un id UUID creado en la tablet,
-- así que no chocan entre dispositivos. updated_at permite bajar solo lo que cambió.

create table public.shared_stores (
  id text primary key,
  name text not null,
  latitude double precision not null,
  longitude double precision not null,
  phone text,
  archived boolean not null default false,
  updated_by uuid references auth.users (id) default auth.uid(),
  updated_at timestamptz not null default now()
);
create index shared_stores_updated on public.shared_stores (updated_at);

create function public.touch_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  new.updated_by := auth.uid();
  return new;
end $$;

create trigger shared_stores_touch
  before insert or update on public.shared_stores
  for each row execute function public.touch_updated_at();

alter table public.shared_stores enable row level security;
create policy "usuarios leen tiendas" on public.shared_stores for select to authenticated using (true);
create policy "usuarios agregan tiendas" on public.shared_stores for insert to authenticated with check (true);
create policy "usuarios editan tiendas" on public.shared_stores for update to authenticated using (true) with check (true);

-- Lo que ya subieron las tabletas pasa al catálogo compartido.
insert into public.shared_stores (id, name, latitude, longitude, phone, archived)
select distinct on (id) id, name, latitude, longitude, phone, archived from public.stores
on conflict (id) do nothing;
