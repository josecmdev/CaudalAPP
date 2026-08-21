# Caudal App

Aplicación Android para gestionar rutas de venta, inventario, tiendas geolocalizadas, cuentas pendientes, envases retornables, gastos e historial GPS.

## Tecnología

- Kotlin y Jetpack Compose
- MapLibre
- Persistencia local con JSON y preferencias de Android
- Pruebas unitarias con JUnit

## Abrir el proyecto

1. Abre esta carpeta desde Android Studio.
2. Espera la sincronización de Gradle.
3. Conecta la tablet o selecciona un emulador.
4. Presiona **Run**. Android Studio compilará e instalará la aplicación.

## Verificación

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
```

El proyecto no guarda `local.properties`, compilaciones ni configuraciones personales de Android Studio en Git.

Consulta [docs/GITFLOW.md](docs/GITFLOW.md) para conocer el flujo de ramas y [docs/MARCADORES_PERSONALIZADOS.md](docs/MARCADORES_PERSONALIZADOS.md) para reemplazar los íconos del mapa.
