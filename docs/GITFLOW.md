# GitFlow de Caudal App

Caudal App usa un flujo GitFlow sencillo:

- `main`: versiones estables que pueden entregarse o instalarse.
- `develop`: integración de funcionalidades terminadas para la próxima versión.
- `feature/*`: una funcionalidad o grupo de cambios concreto.
- `release/*`: estabilización final antes de publicar una versión.
- `hotfix/*`: correcciones urgentes que parten de una versión publicada.

## Flujo normal

1. Crear `feature/nombre` desde `develop`.
2. Programar y probar únicamente esa funcionalidad.
3. Integrarla en `develop` mediante un merge explícito.
4. Crear `release/x.y.z` cuando el conjunto esté listo.
5. Probar la versión y fusionarla en `main` y `develop`.
6. Etiquetar el commit estable, por ejemplo `v1.0.0`.

Este modelo mantiene `main` estable, permite preparar varias mejoras en `develop` y conserva en el historial el propósito de cada cambio. Para un equipo pequeño no hace falta instalar la extensión `git flow`; los mismos pasos se realizan con comandos Git normales y ramas con nombres consistentes.
