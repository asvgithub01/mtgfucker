# Configurar Google Login y Firestore

La app compila sin credenciales, pero muestra **Firebase pendiente de configurar** hasta que se
añada el archivo real del proyecto. `google-services.json` está ignorado por Git para que cada
entorno pueda usar su propio proyecto Firebase.

## 1. Crear y registrar el proyecto

1. Entra en <https://console.firebase.google.com/> y crea un proyecto.
2. Añade una aplicación **Android** con este package name exacto:
   `io.asv.mtgocr.ocrreader`.
3. En una terminal, desde la raíz del proyecto, ejecuta:

   ```powershell
   .\gradlew signingReport
   ```

4. En **Configuración del proyecto > Tus apps > Android**, añade las huellas **SHA-1** y
   **SHA-256** de `debug`. Para una versión publicada, añade también las de la clave de release o
   las de **Play App Signing**.

## 2. Activar Google y descargar el archivo

1. En **Authentication > Sign-in method**, habilita **Google** y guarda.
2. Vuelve a **Configuración del proyecto > Tus apps > Android**.
3. Pulsa **Descargar google-services.json**. Es importante volver a descargarlo después de
   habilitar Google, para que incluya el cliente OAuth web usado por Credential Manager.
4. Copia el archivo, sin renombrarlo, en:

   ```text
   app/google-services.json
   ```

5. Vuelve a compilar. Gradle detecta el archivo y activa automáticamente el plugin de Google.

## 3. Crear Firestore y publicar reglas

1. En **Firestore Database**, crea una base de datos de edición Standard en modo producción y
   elige la región que corresponda a tus usuarios.
2. Copia el contenido de `firestore.rules` en la pestaña **Reglas** y publícalo.

Como alternativa, con Firebase CLI:

```powershell
Copy-Item .firebaserc.example .firebaserc
# Sustituye TU_ID_DE_PROYECTO_FIREBASE por el id real.
firebase login
firebase deploy --only firestore:rules
```

Las reglas aíslan toda la copia bajo `users/{uid}`: una cuenta autenticada no puede leer ni
escribir las bibliotecas de otra.

## 4. Probar el ciclo Premium

1. Instala la app, activa el flag Premium local en **Ajustes** y abre
   **Cuenta de Google y copia en la nube**.
2. Inicia sesión con Google y pulsa **Sincronizar ahora**.
3. Añade o modifica cartas; los guardados posteriores se suben automáticamente tras una pausa
   corta, y el botón manual permite confirmar la fecha del último sync.
4. Desactiva Premium: la colección local sigue funcionando, pero no se sube nada y la última
   copia Firestore se conserva.
5. Borra los datos o desinstala la app, reinstálala, vuelve a colocar Premium, inicia sesión con
   la misma cuenta y se recuperará automáticamente la última copia si la Biblio local está vacía.
   También puedes usar **Descargar última copia** para forzar la restauración.

> Para producción, el flag local debe sustituirse por un entitlement validado por el backend
> (por ejemplo Play Billing + custom claim). La implementación actual respeta el sistema Premium
> ya existente en el proyecto y está pensada para poder probar todo el flujo sin una tienda.
