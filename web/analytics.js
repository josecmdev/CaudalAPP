// Cálculos del panel, sin dependencias para poder probarlos con node (analytics.test.js).
(function (root) {
  const DAYS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
  // Guatemala no usa horario de verano: siempre UTC-6.
  const guatemalaDay = (iso) => new Date(Date.parse(iso) - 6 * 3600 * 1000).getUTCDay();

  function groupSum(items, keyOf, valueOf) {
    const map = new Map();
    for (const item of items) map.set(keyOf(item), (map.get(keyOf(item)) || 0) + valueOf(item));
    return map;
  }

  function kpis(lines, routes) {
    const sales = lines.reduce((sum, l) => sum + l.total, 0);
    const expenses = routes.reduce((sum, r) => sum + r.total_expenses, 0);
    const saleCount = new Set(lines.map((l) => l.device_id + l.sale_id)).size;
    return {
      sales,
      units: lines.reduce((sum, l) => sum + l.delivered, 0),
      expenses,
      profit: routes.reduce((sum, r) => sum + r.total_sales, 0) - expenses,
      routes: routes.length,
      averageTicket: saleCount ? Math.round(sales / saleCount) : 0,
      pending: routes.reduce((sum, r) => sum + (r.money_pending || 0), 0),
    };
  }

  function topProducts(lines) {
    const revenue = groupSum(lines, (l) => l.product_name, (l) => l.total);
    const units = groupSum(lines, (l) => l.product_name, (l) => l.delivered);
    return [...revenue.entries()]
      .map(([name, total]) => ({ name, total, units: units.get(name) }))
      .sort((a, b) => b.total - a.total);
  }

  function prices(lines) {
    const byProduct = new Map();
    for (const l of lines) {
      if (l.delivered <= 0) continue;
      const unit = l.total / l.delivered;
      const p = byProduct.get(l.product_name) || { name: l.product_name, total: 0, units: 0, min: Infinity, max: 0 };
      p.total += l.total; p.units += l.delivered; p.min = Math.min(p.min, unit); p.max = Math.max(p.max, unit);
      byProduct.set(l.product_name, p);
    }
    return [...byProduct.values()]
      .map((p) => ({ name: p.name, average: p.total / p.units, min: p.min, max: p.max }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  // "Ganancia" = ventas de la ruta menos sus gastos; no incluye costo de producto (la app aún no lo registra).
  function weekdays(routes) {
    const stats = DAYS.map((name) => ({ name, routes: 0, sales: 0, profit: 0 }));
    for (const r of routes) {
      const s = stats[guatemalaDay(r.started_at)];
      s.routes += 1; s.sales += r.total_sales; s.profit += r.total_sales - r.total_expenses;
    }
    return stats.map((s) => ({
      ...s,
      averageSales: s.routes ? Math.round(s.sales / s.routes) : 0,
      averageProfit: s.routes ? Math.round(s.profit / s.routes) : 0,
    }));
  }

  function bestDay(routes) {
    const candidates = weekdays(routes).filter((d) => d.routes > 0);
    return candidates.sort((a, b) => b.averageProfit - a.averageProfit)[0] || null;
  }

  function dailyTrend(lines) {
    return [...groupSum(lines, (l) => l.sale_date, (l) => l.total).entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, total]) => ({ date, total }));
  }

  const api = { kpis, topProducts, prices, weekdays, bestDay, dailyTrend, guatemalaDay };
  if (typeof module !== 'undefined') module.exports = api; else root.CaudalAnalytics = api;
})(this);
