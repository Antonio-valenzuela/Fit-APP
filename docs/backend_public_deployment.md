# Guía de Despliegue Público del Backend y Pruebas en Exterior

Esta guía detalla los pasos para desplegar el servidor backend proxy Node.js en internet, configurar la app de Android para usar la URL pública HTTPS y poder realizar pruebas en el exterior (restaurantes, calle, etc.) con datos móviles y GPS activo.

---

## 1. Dónde Desplegar el Backend

Puedes desplegar este backend de Node.js de forma rápida y gratuita (o de bajo costo) en las siguientes plataformas:

* **Render** (Recomendado): Muy fácil de conectar directamente con tu repositorio de GitHub.
* **Railway**: Excelente alternativa rápida con despliegue automático.
* **Fly.io**: Excelente opción para despliegues ligeros en Docker.
* **VPS propio** (DigitalOcean, AWS LightSail, etc.): Si tienes control total del servidor.

### Pasos Generales para Desplegar (ej. Render):
1. Sube el código del backend a tu repositorio de GitHub (puedes subir la carpeta raíz o crear un repositorio separado para el backend).
2. Regístrate en [Render.com](https://render.com) e inicia sesión.
3. Haz clic en **New** > **Web Service**.
4. Selecciona tu repositorio de GitHub.
5. Configura los siguientes parámetros básicos:
   * **Runtime**: `Node`
   * **Build Command**: `npm install`
   * **Start Command**: `node index.js`
   * **Instance Type**: `Free` (o el de tu preferencia)

---

## 2. Variables de Entorno del Servidor Backend

En la sección de configuraciones (Environment Variables) de tu servicio de hosting, debes ingresar las siguientes llaves:

* `PORT`: `3000` (o el puerto provisto por defecto por el hosting).
* `BACKEND_API_KEY`: Una llave secreta estática para autenticar la app (debe coincidir con la de la app, ej. `fitplan_secret_key_123`).
* `GEMINI_API_KEY`: Tu API Key real de Google Gemini (obtenida de Google AI Studio). El servidor la usará para proxy sin exponerla en la app.
* `GEMINI_TIMEOUT_MS`: `60000` (tiempo límite para llamadas a Gemini).

---

## 3. Obtener y Configurar la URL HTTPS en la App

1. Una vez completado el despliegue del backend, el hosting te proporcionará una URL HTTPS pública, por ejemplo:
   `https://fitplan-api-staging.onrender.com`
2. Copia esa URL.
3. Abre el archivo `.env` de tu proyecto Android (`C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\.env`).
4. Reemplaza o edita el valor de `BACKEND_BASE_URL` para que apunte a tu URL pública:
   ```env
   BACKEND_BASE_URL=https://fitplan-api-staging.onrender.com
   ```

---

## 4. Reconstruir e Instalar la App en Celular Físico

Una vez que configuraste la URL pública en el `.env`, realiza los siguientes pasos para instalar el nuevo APK de pruebas:

1. Asegúrate de tener habilitada la **Depuración por USB** en tu celular Android (Ajustes > Opciones de desarrollador).
2. Conecta tu celular a la PC mediante cable USB.
3. Compila e instala la aplicación ejecutando en la terminal de la PC:
   ```powershell
   .\gradlew.bat app:installDebug
   ```
   *Alternativamente, puedes compilar el APK con `.\gradlew.bat app:assembleDebug` y transferir el archivo APK generado en `app/build/outputs/apk/debug/app-debug.apk` a tu celular para instalarlo manualmente.*

---

## 5. Pruebas Reales con Datos Móviles

1. Desconecta tu celular del WiFi y activa la conexión de **Datos Móviles** (4G/5G).
2. Abre la app **FitPlan MX**.
3. Navega al apartado de comida/nutrición y realiza un **escaneo de comida con IA**:
   * Si la app y el backend público están bien vinculados, la foto se subirá al servidor remoto en HTTPS, este procesará la información con Gemini y la app registrará correctamente tus calorías y macros en tiempo real sin importar que estés en la calle o en un restaurante.
4. **Verificación de Errores**:
   * Si ocurre algún fallo de conexión, el usuario verá el mensaje amigable: `"No se pudo analizar la comida. Revisa tu conexión o intenta de nuevo."`
   * Puedes ver el error técnico detallado conectando el celular a la PC y revisando Android Studio Logcat con el filtro `GeminiService`.

---

## 6. Resumen de Pasos de Prueba Finales

### Paso 7: Actualiza la app Android con la URL real
Cuando Render te proporcione la URL real del Web Service, abre el archivo [.env](file:///C:/Users/yahir/Desktop/FIT-APP%20MAS%20ACTUALIDADA/.env) de tu proyecto Android y configura:
```env
BACKEND_BASE_URL=https://tu-app-de-render.onrender.com
BACKEND_API_KEY=fitplan_secret_key_123
```
*(Asegúrate de poner la URL sin la barra `/` final).*

### Paso 8: Reconstruir e instalar la app
Cada vez que cambies la URL en el `.env`, realiza una limpieza completa de la caché para evitar referencias antiguas en `BuildConfig`. Abre PowerShell y ejecuta:
```powershell
cd "C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA"
.\gradlew.bat clean
.\gradlew.bat app:assembleDebug
.\gradlew.bat app:installDebug
```

### Paso 9: Prueba real con datos móviles
En tu celular Android físico:
1. Apaga el WiFi y mantén activos únicamente los **Datos Móviles** (4G/5G).
2. Abre tu navegador web en el celular e ingresa a: `https://tu-app-de-render.onrender.com/health`
3. Si responde `{ "status": "UP" }`, abre **FitPlan MX**.
4. Ve al escaneo de comida con IA y realiza una prueba subiendo una foto. El procesamiento debe completarse de forma remota sin requerir que estés conectado al WiFi ni tener la PC encendida.
