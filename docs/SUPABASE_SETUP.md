# Configurar Supabase (gratis) para Caudal

La app sigue funcionando igual sin esto. Con Supabase configurado, cada tableta con sesión iniciada sube cada minuto (si hay internet) sus ventas, rutas, movimientos (cargas, gastos, cobros), tiendas, productos y última ubicación. La página `web/` es el panel de la oficina.

## 1. Crear el proyecto

1. Entra a https://supabase.com y crea una cuenta (puedes entrar con GitHub).
2. **New project**. Nombre: `caudal`. Pon una contraseña de base de datos y guárdala. Región: la más cercana (East US). Plan: **Free**.
3. Espera 1 o 2 minutos a que termine de crearse.

## 2. Crear las tablas

1. Menú izquierdo → **SQL Editor** → **New query**.
2. Abre `supabase/migrations/20261123000000_caudal_sync.sql` de este repositorio, copia todo, pégalo y toca **Run**. Debe decir "Success".

## 3. Cerrar el registro público

1. **Authentication** → **Sign In / Providers**.
2. Deja **Email** activado y apaga **Allow new users to sign up**. Así solo el administrador crea cuentas. No hace falta Google.

## 4. Crear usuarios

1. **Authentication** → **Users** → **Add user** → **Create new user**.
2. Correo: puede ser real o inventado con `@caudal.local`, por ejemplo `admin@caudal.local`, `ruta1@caudal.local`. Pon la contraseña y marca **Auto Confirm User**.
3. En la tablet y en la web se puede entrar escribiendo solo `ruta1` (la app agrega `@caudal.local`).
4. Todos nacen como **vendedor**. Para hacer administrador a uno, en **SQL Editor** corre:

   ```sql
   update public.profiles set role = 'admin' where username = 'admin';
   ```

   (`username` es lo que va antes de la @.)

## 5. Copiar las llaves

**Project Settings** → **API** (o **Data API**). Copia:

- **Project URL** (ej. `https://abcd1234.supabase.co`)
- **anon public** key (texto largo que empieza con `eyJ...`). No uses la `service_role`: esa nunca va en la app ni en la web.

## 6. App de las tabletas

1. En `local.properties` (en la raíz del proyecto, no se sube a git) agrega:

   ```
   supabase.url=https://abcd1234.supabase.co
   supabase.anonKey=eyJ...
   ```

2. Compila e instala la misma APK en cada tableta (Android Studio, variante release).
3. En cada tableta: **Ajustes** → **Cuenta y sincronización** → usuario y contraseña → **Entrar**. Si la tableta no tiene internet, la app sigue funcionando y sube todo cuando vuelva la conexión.

## 7. Página web (panel del administrador)

1. Copia `web/config.example.js` como `web/config.js` y pega la URL y la llave anon. Esta llave es pública por diseño; los datos los protegen las reglas RLS y las contraseñas. Puedes subir `config.js` al repositorio.
2. Hosting gratis, cualquiera de estas:
   - **Cloudflare Pages** (acepta repos privados): Workers & Pages → Create → Pages → Connect to Git → elige `CaudalAPP` → Build command vacío → Build output directory `web` → Deploy.
   - **Netlify Drop**: https://app.netlify.com/drop y arrastra la carpeta `web`.
   - Sin hosting: abre `web/index.html` con doble clic en la computadora.
3. Entra con la cuenta admin. Verás tabletas (toca una para ver su última ubicación en el mapa), productos más vendidos, precios, ganancia (ventas menos gastos de ruta) y qué día de la semana conviene salir.

## Notas del plan gratis

- Si el proyecto pasa **7 días sin uso**, Supabase lo **pausa, pero no borra nada**. Se reactiva desde el dashboard con **Restore project**. Mientras las tabletas sincronicen, no se pausa.
- Límite: 500 MB de base de datos. Una posición por minuto por tableta da para años.
- La ganancia no resta costo de producto porque la app todavía no lo registra.
- El respaldo en archivo de Ajustes sigue existiendo y es independiente de esto.
