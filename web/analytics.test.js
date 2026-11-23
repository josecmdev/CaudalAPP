// node web/analytics.test.js
const assert = require('assert');
const a = require('./analytics.js');

const lines = [
  { device_id: 't', sale_id: '1', product_name: 'Bolsa', total: 20, delivered: 4, sale_date: '2026-11-02' },
  { device_id: 't', sale_id: '1', product_name: 'Garrafón', total: 30, delivered: 2, sale_date: '2026-11-02' },
  { device_id: 't', sale_id: '2', product_name: 'Bolsa', total: 18, delivered: 3, sale_date: '2026-11-03' },
];
// 2026-11-02T03:00Z es domingo 21:00 en Guatemala, no lunes.
const routes = [
  { started_at: '2026-11-02T03:00:00Z', total_sales: 100, total_expenses: 10, money_pending: 5 },
  { started_at: '2026-11-03T14:00:00Z', total_sales: 300, total_expenses: 20, money_pending: 0 },
];

assert.strictEqual(a.guatemalaDay(routes[0].started_at), 0);
const k = a.kpis(lines, routes);
assert.deepStrictEqual([k.sales, k.units, k.profit, k.averageTicket], [68, 9, 370, 34]);
assert.strictEqual(a.topProducts(lines)[0].name, 'Bolsa');
const bolsa = a.prices(lines).find((p) => p.name === 'Bolsa');
assert.deepStrictEqual([bolsa.min, bolsa.max], [5, 6]);
assert.strictEqual(a.bestDay(routes).name, 'Martes');
assert.deepStrictEqual(a.dailyTrend(lines).map((d) => d.total), [50, 18]);
console.log('analytics ok');
