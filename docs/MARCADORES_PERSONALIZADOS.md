# Marcadores personalizados de tiendas

Caudal App busca automáticamente los íconos PNG en esta carpeta:

`app/src/main/assets/map/markers/`

Los nombres deben ser exactamente estos:

- `store_debt.png`: tienda con deuda monetaria.
- `store_pending_delivery.png`: entrega de producto pendiente.
- `store_recent.png`: tienda atendida recientemente.
- `store_due_soon.png`: tienda que podría volver a comprar.
- `store_inactive.png`: tienda con más tiempo sin comprar.

## Preparación recomendada

- PNG con fondo transparente.
- Todos los archivos con las mismas dimensiones; por ejemplo, 192 x 224 px.
- El dibujo debe estar centrado y la punta inferior debe indicar la ubicación exacta.
- Deja margen transparente alrededor para evitar que se corte la ilustración.
- Conserva una forma común y cambia el color según el estado.

Después de copiar los archivos, ejecuta de nuevo la aplicación desde Android Studio. No es necesario modificar Kotlin. Si falta alguno, Caudal App utiliza automáticamente el marcador provisional de ese estado.
