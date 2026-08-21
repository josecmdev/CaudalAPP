# Caudal App — requisitos acordados para el prototipo

## Alcance

Caudal App es una aplicación privada para una tablet Android que sustituye el
control diario de rutas de venta hecho en papel. Funciona localmente y sin
inicio de sesión. La arquitectura debe permitir incorporar persistencia,
sincronización y copias de seguridad en fases posteriores.

## Ruta activa

- Solo puede existir una ruta activa.
- La ruta se inicia con nombre libre, sencillo inicial e inventario escrito
  manualmente.
- El GPS solicita una posición por segundo y la ruta se recupera si Android
  cierra la interfaz o reinicia la tablet.
- El rumbo procede del GPS mientras el vehículo se mueve; al detenerse se
  conserva el último rumbo válido.
- El mapa funciona offline dentro del área descargada, inicia centrado en la
  ubicación actual y vuelve automáticamente al seguimiento después de que el
  usuario deja de manipularlo.
- El cronómetro muestra horas y minutos. Salir abre el resumen; la ruta solo se
  cierra al pulsar `Finalizar ruta`.

## Ventas e inventario

- Cada renglón contiene producto, cantidad y total negociado en quetzales
  enteros.
- Una factura puede recibir un pago parcial. La diferencia crea una deuda de
  la tienda.
- Las ventas rápidas son únicamente al contado.
- El inventario puede mostrar una disponibilidad negativa cuando existe una
  entrega pendiente.
- Una entrega pendiente conserva cantidad solicitada, entregada y faltante. Al
  completarla se descuenta la reposición y se cobra el total pendiente.
- Los precios son editables. La sugerencia prioriza el último precio usado para
  la misma tienda, producto y cantidad, y después el precio general.
- Una factura en preparación se edita libremente. Una venta confirmada se
  corrige o anula dejando registro.

## Envases retornables

- Garrafones y cilindros separan contenido y envase.
- Normalmente se vende el contenido y se presta el envase. Por cada lleno
  entregado se espera un vacío.
- Si no vuelve el vacío, la tienda queda debiendo el envase sin cargo
  monetario automático.
- El envase puede venderse junto con el contenido, incluso fiado; entonces ya
  no se espera su devolución.
- Los envases adicionales recibidos aumentan los vacíos del camión sin crear
  saldo a favor.
- La sede permite cargar llenos y descargar vacíos. Las transferencias con
  Kenneth distinguen llenos y vacíos y conservan historial.

## Tiendas y mapa

- Todas las tiendas son visibles en cualquier ruta.
- Se agregan moviendo el mapa debajo de una mira fija y confirmando el centro.
- Los marcadores se agrupan al alejar el mapa y admiten una imagen ilustrada
  reemplazable.
- Prioridad visual: deuda monetaria roja, entrega pendiente naranja y después
  estado por antigüedad (verde, amarillo o gris). Las obligaciones adicionales
  se muestran mediante insignias.
- Una tienda archivada desaparece del mapa normal, pero conserva historial,
  deuda y envases pendientes.

## Caja, gastos e historial

- Efectivo esperado = sencillo inicial + ventas cobradas + cobros de deudas -
  gastos.
- Categorías iniciales de gasto: combustible, refacción y otro. Los gastos se
  pueden corregir o anular con historial.
- El cierre puede conservar entregas, deudas y envases pendientes para rutas
  posteriores.
- El historial resume ventas, efectivo, fiado, gastos, inventario, envases,
  transferencias, anulaciones y recorrido sobre el mapa.

## Interfaz de ruta

- Tema claro azul, alto contraste y animaciones rápidas.
- Cronómetro centrado arriba y botón Salir arriba a la derecha.
- Botón `+` independiente y siempre visible para venta rápida, agregar tienda,
  recargar en sede, transferir con Kenneth y registrar gasto.
- Panel auxiliar inferior en vertical y lateral derecho en horizontal. Muestra
  nombres y cantidades de productos, dinero y pendientes sin tarjetas
  innecesarias.
- Al confirmar una operación se bloquea el botón, vibra el dispositivo y se
  muestra brevemente el cambio de inventario y efectivo.
