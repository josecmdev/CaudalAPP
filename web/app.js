const $ = (id) => document.getElementById(id);
const Q = (n) => 'Q' + Math.round(n).toLocaleString('es-GT');
const when = (iso) => (iso ? new Date(iso).toLocaleString('es-GT', { dateStyle: 'medium', timeStyle: 'short' }) : '—');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const config = window.CAUDAL_CONFIG;
if (!config || !config.supabaseUrl || !config.supabaseAnonKey) {
  $('missing-config').hidden = false;
} else {
  start(window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey));
}

function start(db) {
  const charts = {};
  let map, markers = {}, storeLayer, fitted = false;

  $('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    $('login-error').textContent = '';
    const user = $('user').value.trim();
    const email = (user.includes('@') ? user : `${user}@caudal.local`).toLowerCase();
    const { error } = await db.auth.signInWithPassword({ email, password: $('password').value });
    if (error) $('login-error').textContent = error.code === 'email_not_confirmed' ? 'La cuenta no está confirmada en Supabase.' : error.code === 'invalid_credentials' ? 'Usuario o contraseña incorrectos.' : `No se pudo entrar: ${error.message}`;
    else show();
  });
  $('logout').addEventListener('click', async () => { await db.auth.signOut(); location.reload(); });
  $('refresh').addEventListener('click', load);
  $('period').addEventListener('change', load);
  show();
  // Cada 2 min y solo con la pestaña a la vista: cuida el límite gratis de datos de Supabase.
  setInterval(() => { if (!document.hidden && !$('app').hidden) load(); }, 120000);

  async function show() {
    const { data: { session } } = await db.auth.getSession();
    if (!session) { $('login').hidden = false; $('app').hidden = true; return; }
    const { data: profile } = await db.from('profiles').select('role').eq('id', session.user.id).maybeSingle();
    if (profile?.role !== 'admin') {
      await db.auth.signOut();
      $('login').hidden = false;
      $('login-error').textContent = 'Esta cuenta no es de administrador.';
      return;
    }
    $('login').hidden = true;
    $('app').hidden = false;
    if (!map) {
      map = L.map('map').setView([14.64, -90.51], 11);
      // OSM exige que la página se sirva por http(s) (Cloudflare Pages o servidor local); desde file:// sale 403.
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap' }).addTo(map);
    }
    load();
  }

  // PostgREST entrega máximo 1000 filas por llamada.
  async function fetchAll(query) {
    const rows = [];
    for (let from = 0; ; from += 1000) {
      const { data, error } = await query().range(from, from + 999);
      if (error) throw error;
      rows.push(...data);
      if (data.length < 1000) return rows;
    }
  }

  async function load() {
    const days = Number($('period').value);
    const since = days ? new Date(Date.now() - days * 86400000).toISOString() : '1970-01-01T00:00:00Z';
    try {
      // Las tiendas van en la misma carga: una petición por recarga.
      const [lines, routes, devices, profiles, stores, accounts] = await Promise.all([
        fetchAll(() => db.from('sales_report').select('*').gte('recorded_at', since).order('recorded_at')),
        fetchAll(() => db.from('routes').select('*').eq('archived', false).gte('started_at', since).order('started_at', { ascending: false })),
        fetchAll(() => db.from('devices').select('*').order('last_seen_at', { ascending: false })),
        fetchAll(() => db.from('profiles').select('id, username')),
        fetchAll(() => db.from('shared_stores').select('id, name, latitude, longitude, phone').eq('archived', false).order('id')),
        // Sin la migración store_accounts la página sigue funcionando; solo avisa en la tarjeta.
        fetchAll(() => db.from('store_accounts').select('*').order('store_name')).catch(() => null),
      ]);
      const names = Object.fromEntries(profiles.map((p) => [p.id, p.username]));
      renderKpis(CaudalAnalytics.kpis(lines, routes), accounts);
      renderStores(stores);
      renderDevices(devices, names); if (!fitted) { const points = [...Object.values(markers).map((m) => m.getLatLng()), ...storeLayer.getLayers().map((l) => l.getLatLng())]; if (points.length) { map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 }); fitted = true; } } if (!fitted) { const points = [...Object.values(markers).map((m) => m.getLatLng()), ...storeLayer.getLayers().map((l) => l.getLatLng())]; if (points.length) { map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 }); fitted = true; } }
      renderCharts(lines, routes);
      renderPrices(CaudalAnalytics.prices(lines));
      renderRoutes(routes, devices, names);
      renderAccounts(accounts, devices, names);
    } catch (error) {
      $('kpis').innerHTML = `<p class="error">No se pudieron cargar los datos: ${esc(error.message)}</p>`;
    }
  }

  function renderKpis(k, accounts) {
    const items = [
      ['Ventas', Q(k.sales)], ['Unidades', k.units.toLocaleString('es-GT')], ['Gastos de ruta', Q(k.expenses)],
      ['Ganancia (ventas − gastos)', Q(k.profit)], ['Rutas', k.routes], ['Venta promedio', Q(k.averageTicket)], ['Por cobrar al cierre', Q(k.pending)],
    ];
    if (accounts) items.push(['Deben las tiendas', Q(accounts.reduce((sum, a) => sum + a.money_due, 0))]);
    $('kpis').innerHTML = items.map(([label, value]) => `<div class="kpi"><span>${label}</span><strong>${value}</strong></div>`).join('');
  }

  function renderStores(stores) {
    storeLayer?.remove();
    storeLayer = L.layerGroup(stores.map((s) => L.circleMarker([s.latitude, s.longitude], { radius: 5, color: '#1565c0', weight: 1, fillOpacity: 0.7 })
      .bindPopup(`<b>${esc(s.name)}</b>${s.phone ? '<br>' + esc(s.phone) : ''}`))).addTo(map);
  }

  function renderDevices(devices, names) {
    Object.values(markers).forEach((m) => m.remove());
    markers = {};
    $('devices').innerHTML = devices.length ? '' : '<li class="muted">Ninguna tableta ha sincronizado todavía.</li>';
    for (const d of devices) {
      const label = `${esc(names[d.user_id] || 'vendedor')} · ${esc(d.name)}`;
      if (d.last_latitude != null) {
        markers[d.id] = L.marker([d.last_latitude, d.last_longitude]).addTo(map)
          .bindPopup(`<b>${label}</b><br>Ubicación: ${when(d.last_position_at)}`);
      }
      const li = document.createElement('li');
      li.innerHTML = `<button>${label}<small>Visto: ${when(d.last_seen_at)} · ${d.last_latitude != null ? 'con ubicación' : 'sin ubicación aún'}</small></button>`;
      li.querySelector('button').addEventListener('click', () => {
        const m = markers[d.id];
        if (m) { map.setView(m.getLatLng(), 15); m.openPopup(); }
      });
      $('devices').append(li);
    }
  }

  function chart(id, type, labels, datasets, options = {}) {
    charts[id]?.destroy();
    charts[id] = new Chart($(id), { type, data: { labels, datasets }, options: { plugins: { legend: { display: datasets.length > 1 } }, ...options } });
  }

  function renderCharts(lines, routes) {
    const top = CaudalAnalytics.topProducts(lines).slice(0, 10);
    chart('chart-products', 'bar', top.map((p) => p.name), [{ label: 'Ventas Q', data: top.map((p) => p.total), backgroundColor: '#1e88e5' }], { indexAxis: 'y' });
    const week = CaudalAnalytics.weekdays(routes);
    chart('chart-weekday', 'bar', week.map((d) => d.name.slice(0, 3)), [
      { label: 'Venta promedio', data: week.map((d) => d.averageSales), backgroundColor: '#1e88e5' },
      { label: 'Ganancia promedio', data: week.map((d) => d.averageProfit), backgroundColor: '#43a047' },
    ]);
    const best = CaudalAnalytics.bestDay(routes);
    $('best-day').textContent = best
      ? `Mejor día: ${best.name}, con ganancia promedio de ${Q(best.averageProfit)} por ruta (${best.routes} rutas).`
      : 'Aún no hay rutas en este periodo.';
    const trend = CaudalAnalytics.dailyTrend(lines);
    chart('chart-trend', 'line', trend.map((d) => d.date), [{ label: 'Ventas Q', data: trend.map((d) => d.total), borderColor: '#1e88e5', tension: 0.2 }]);
  }

  function renderPrices(rows) {
    $('prices').innerHTML = '<tr><th>Producto</th><th class="num">Promedio</th><th class="num">Mínimo</th><th class="num">Máximo</th></tr>' +
      rows.map((p) => `<tr><td>${esc(p.name)}</td><td class="num">Q${p.average.toFixed(2)}</td><td class="num">Q${p.min.toFixed(2)}</td><td class="num">Q${p.max.toFixed(2)}</td></tr>`).join('');
  }

  function renderRoutes(routes, devices, names) {
    const owner = Object.fromEntries(devices.map((d) => [d.id, names[d.user_id] || d.name]));
    $('routes').innerHTML = '<tr><th>Inicio</th><th>Ruta</th><th>Vendedor</th><th>Estado</th><th class="num">Ventas</th><th class="num">Gastos</th><th class="num">Ganancia</th><th class="num">Efectivo esperado</th><th class="num">Contado</th></tr>' +
      routes.map((r) => `<tr><td>${when(r.started_at)}</td><td>${esc(r.name)}</td><td>${esc(owner[r.device_id] || '')}</td><td>${r.status}</td>` +
        `<td class="num">${Q(r.total_sales)}</td><td class="num">${Q(r.total_expenses)}</td><td class="num">${Q(r.total_sales - r.total_expenses)}</td>` +
        `<td class="num">${Q(r.expected_cash)}</td><td class="num">${r.counted_cash == null ? '—' : Q(r.counted_cash)}</td></tr>`).join('');
  }

  function renderAccounts(accounts, devices, names) {
    if (!accounts) { $('accounts').innerHTML = '<tr><td class="error">Falta correr la migración store_accounts en Supabase.</td></tr>'; return; }
    const owner = Object.fromEntries(devices.map((d) => [d.id, names[d.user_id] || d.name]));
    const units = (map) => Object.entries(map || {}).map(([name, n]) => `${n} ${esc(name)}`).join(', ') || '—';
    const open = accounts.filter((a) => a.money_due > 0 || a.paid > 0 || Object.keys(a.pending_deliveries).length || Object.keys(a.pending_containers).length);
    $('accounts').innerHTML = open.length
      ? '<tr><th>Tienda</th><th>Vendedor</th><th class="num">Debe</th><th class="num">Ya pagó</th><th>Entregas pendientes</th><th>Envases pendientes</th><th>Actualizado</th></tr>' +
        open.map((a) => `<tr><td>${esc(a.store_name)}</td><td>${esc(owner[a.device_id] || '')}</td><td class="num">${Q(a.money_due)}</td><td class="num">${Q(a.paid)}</td>` +
          `<td>${units(a.pending_deliveries)}</td><td>${units(a.pending_containers)}</td><td>${when(a.updated_at)}</td></tr>`).join('')
      : '<tr><td class="muted">Ninguna tienda debe nada.</td></tr>';
  }
}
