# Caudal App

App Android privada (tablet, sin login, local) que reemplaza el control en papel de las rutas de venta de una purificadora: agua en bolsa, garrafones, gas y huevo. Moneda: quetzales enteros.

## Stack
- Kotlin + Jetpack Compose, MapLibre (mapa offline), Android Auto (`car/`).
- Persistencia local: JSON + preferencias (`persistence/`). Sin backend.
- minSdk 24, targetSdk 36. Paquete `com.example.caudalapp`.

## Estructura
- `domain/`: reglas de negocio puras (políticas, `RouteLedger`, directorios). Toda lógica nueva va aquí con su prueba.
- Raíz del paquete: pantallas y diálogos Compose, servicios (GPS: `RouteTrackingService`).
- Núcleo: `GeoPoint`, `Product`, `SaleLine`, `Store`, `StoreAccountState`, `ActiveRoute`, `RouteLedger`.
- Requisitos completos: `docs/PRODUCT_REQUIREMENTS.md`. Leerlo antes de cambiar reglas de negocio.

## Reglas de negocio clave
- Una sola ruta activa. Pago parcial genera deuda de la tienda; venta rápida solo al contado.
- Garrafones y cilindros: contenido y envase van separados; el envase prestado se debe, sin cargo automático.
- Efectivo esperado = sencillo inicial + ventas cobradas + cobros de deudas - gastos.
- Ventas y gastos confirmados no se borran: se corrigen o anulan dejando historial.

## Comandos
- Pruebas y lint: `.\gradlew.bat testDebugUnitTest lintDebug`

## Git
- GitFlow (`docs/GITFLOW.md`): `feature/*` desde `develop`, merge explícito a `develop`. `main` solo versiones estables.

## Navegar el código
- Hay un grafo en `graphify-out/` (no se sube a git). Para preguntas de arquitectura usar `graphify query "<pregunta>"` antes de leer archivos.
- Tras cambios de código: `graphify update .` (gratis, sin LLM).
