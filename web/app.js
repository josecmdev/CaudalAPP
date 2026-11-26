const $ = (id) => document.getElementById(id);
const Q = (n) => 'Q' + Math.round(n).toLocaleString('es-GT');
const when = (iso) => (iso ? new Date(iso).toLocaleString('es-GT', { dateStyle: 'medium', timeStyle: 'short' }) : '—');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const FRESH_MS = 5 * 60000;
// Camión visto desde arriba, apuntando al norte; se rota con CSS según el rumbo.
const TRUCK_SVG = '<svg viewBox="0 0 32 32" aria-hidden="true"><rect x="9" y="11" width="14" height="18" rx="2" class="cargo"/>' +
  '<rect x="10" y="3" width="12" height="9" rx="3.5" class="cab"/><rect x="11.5" y="4.5" width="9" height="3" rx="1" class="glass"/>' +
  '<rect x="7.5" y="6" width="2" height="4" rx="1" class="tire"/><rect x="22.5" y="6" width="2" height="4" rx="1" class="tire"/>' +
  '<rect x="7.5" y="23" width="2" height="5" rx="1" class="tire"/><rect x="22.5" y="23" width="2" height="5" rx="1" class="tire"/></svg>';

const config = window.CAUDAL_CONFIG;
if (!config || !config.supabaseUrl || !config.supabaseAnonKey) {
  $('missing-config').hidden = false;
} else {
  start(window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey));
}

function start(db) {
  const charts = {};
  let map, markers = {}, storeLayer, fitted = false, loadedAt = 0, kpiValues = {};

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
  // Reloj local, sin peticiones: "hace X min" y camiones que pasan a gris.
  setInterval(tick, 30000);

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
      renderStores(stores, accounts);
      renderDevices(devices, names);
      if (!fitted) {
        const points = [...Object.values(markers).map((t) => t.target), ...storeLayer.getLayers().map((l) => l.getLatLng())];
        if (points.length) { map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16 }); fitted = true; }
      }
      renderCharts(lines, routes);
      renderPrices(CaudalAnalytics.prices(lines));
      renderRoutes(routes, devices, names);
      renderAccounts(accounts, devices, names);
      loadedAt = Date.now();
      tick();
    } catch (error) {
      $('kpis').innerHTML = `<p class="error">No se pudieron cargar los datos: ${esc(error.message)}</p>`;
    }
  }

  function renderKpis(k, accounts) {
    const N = (n) => Math.round(n).toLocaleString('es-GT');
    const items = [
      ['Ventas', k.sales, Q], ['Unidades', k.units, N], ['Gastos de ruta', k.expenses, Q],
      ['Ganancia (ventas − gastos)', k.profit, Q], ['Rutas', k.routes, N], ['Venta promedio', k.averageTicket, Q], ['Por cobrar al cierre', k.pending, Q],
    ];
    if (accounts) items.push(['Deben las tiendas', accounts.reduce((sum, a) => sum + a.money_due, 0), Q]);
    const box = $('kpis');
    // Solo se recrean (y se funden) si cambió el juego de tarjetas; en recargas solo cuentan los números.
    const key = items.map(([label]) => label).join('|');
    if (box.dataset.key !== key) {
      box.dataset.key = key;
      box.innerHTML = items.map(([label], i) => `<div class="kpi" style="--i:${i}"><span>${label}</span><strong></strong></div>`).join('');
    }
    items.forEach(([label, value, fmt], i) => countTo(box.children[i].querySelector('strong'), kpiValues[label] ?? 0, value, fmt));
    kpiValues = Object.fromEntries(items.map(([label, value]) => [label, value]));
  }

  function countTo(el, from, to, fmt) {
    if (reducedMotion.matches || from === to) { el.textContent = fmt(to); return; }
    animate(800, (k) => { el.textContent = fmt(from + (to - from) * k); });
  }

  function animate(ms, step) {
    const t0 = performance.now();
    const frame = (now) => {
      const t = Math.min(1, (now - t0) / ms);
      step(1 - (1 - t) ** 3);
      if (t < 1) requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  }

  const isFresh = (iso) => iso && Date.now() - new Date(iso) < FRESH_MS;

  function tick() {
    if (loadedAt) {
      const min = Math.floor((Date.now() - loadedAt) / 60000);
      $('updated').textContent = min < 1 ? 'Actualizado hace menos de 1 min' : `Actualizado hace ${min} min`;
    }
    for (const t of Object.values(markers)) t.marker.getElement()?.classList.toggle('stale', !isFresh(t.positionAt));
  }

  function renderStores(stores, accounts) {
    const due = {};
    for (const a of accounts || []) due[a.id] = (due[a.id] || 0) + a.money_due;
    storeLayer?.remove();
    storeLayer = L.layerGroup(stores.map((s) => {
      const owes = due[s.id] > 0;
      return L.circleMarker([s.latitude, s.longitude], { radius: owes ? 6 : 5, color: owes ? '#c62828' : '#1565c0', weight: 1, fillOpacity: owes ? 0.85 : 0.7 })
        .bindPopup(`<b>${esc(s.name)}</b>${s.phone ? '<br>' + esc(s.phone) : ''}${owes ? `<br><span class="error">Debe ${Q(due[s.id])}</span>` : ''}`);
    })).addTo(map);
  }

  // Rumbo aproximado en grados (0 = norte); basta para distancias de una ruta.
  function bearing(a, b) {
    const dx = (b.lng - a.lng) * Math.cos((a.lat * Math.PI) / 180);
    return (Math.atan2(dx, b.lat - a.lat) * 180) / Math.PI;
  }

  function renderDevices(devices, names) {
    const seen = new Set();
    $('devices').innerHTML = devices.length ? '' : '<li class="muted">Ninguna tableta ha sincronizado todavía.</li>';
    for (const d of devices) {
      const seller = names[d.user_id] || 'vendedor';
      const label = `${esc(seller)} · ${esc(d.name)}`;
      if (d.last_latitude != null) {
        seen.add(d.id);
        const to = L.latLng(d.last_latitude, d.last_longitude);
        let t = markers[d.id];
        if (!t) {
          const icon = L.divIcon({ className: 'truck', iconSize: [34, 34], iconAnchor: [17, 17], popupAnchor: [0, -14],
            html: `<div class="truck-body">${TRUCK_SVG}</div><span class="truck-label"></span>` });
          t = markers[d.id] = { marker: L.marker(to, { icon, keyboard: false }).addTo(map), heading: 0, target: to };
        } else if (!to.equals(t.target)) {
          t.heading = bearing(t.target, to);
          slide(t, to);
        }
        t.target = to;
        t.positionAt = d.last_position_at;
        const el = t.marker.getElement();
        el.querySelector('.truck-body').style.transform = `rotate(${t.heading}deg)`;
        el.querySelector('.truck-label').textContent = seller;
        t.marker.bindPopup(`<b>${label}</b><br>Ubicación: ${when(d.last_position_at)}`);
      }
      const li = document.createElement('li');
      li.innerHTML = `<button>${label}<small>Visto: ${when(d.last_seen_at)} · ${d.last_latitude != null ? 'con ubicación' : 'sin ubicación aún'}</small></button>`;
      li.querySelector('button').addEventListener('click', () => {
        const t = markers[d.id];
        if (t) { map.setView(t.target, 15); t.marker.openPopup(); }
      });
      $('devices').append(li);
    }
    for (const id of Object.keys(markers)) if (!seen.has(id)) { markers[id].marker.remove(); delete markers[id]; }
  }

  function slide(t, to) {
    const from = t.marker.getLatLng();
    if (reducedMotion.matches) { t.marker.setLatLng(to); return; }
    const run = (t.run = {});
    animate(1500, (k) => {
      // Si llega otra posición a media animación, la anterior deja de mover el marcador.
      if (t.run === run) t.marker.setLatLng([from.lat + (to.lat - from.lat) * k, from.lng + (to.lng - from.lng) * k]);
    });
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
