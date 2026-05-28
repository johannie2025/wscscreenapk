# WscScreen APK — Display Screen Receiver

Application Android native pour **Android TV Box / Smart TV**  
Reçoit et affiche en temps réel les données envoyées par la régie **WiseSmartChurch**.

## Architecture

```
WiseSmartChurch (phone, APK Capacitor)
        │
        │  WebSocket ws://[ip]:9000  +  UDP broadcast :9002
        ▼
WscScreen APK (TV Box / Smart TV)
        │
        │  Affichage plein écran
        ▼
Projecteur / Écran d'église
```

## Fichiers Java

| Fichier | Rôle |
|---|---|
| `SplashActivity` | Splash + saisie IP manuelle fallback |
| `ScreenActivity` | Affichage principal, WebSocket, UDP |
| `WscWsClient` | Client WebSocket OkHttp (auto-reconnect 4s) |
| `WscUdpDiscovery` | Auto-découverte UDP de la régie |
| `WscUdpAnnounce_pour_regie_APK` | *(Référence)* Côté régie : announce UDP |
| `BootReceiver` | Démarrage automatique au boot |
| `Utils` | Utilitaires réseau |

## Build local

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

Push un tag pour déclencher le build + release automatique :

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Secrets requis (pour APK signé release)

| Secret | Description |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Keystore base64 : `base64 -w0 keystore.jks` |
| `ANDROID_KEY_ALIAS` | Alias de la clé |
| `ANDROID_KEY_PASSWORD` | Mot de passe de la clé |
| `ANDROID_STORE_PASSWORD` | Mot de passe du keystore |

## Mode Kiosk

Mot de passe kiosk : **Jesus@2026**  
L'app se déclare comme launcher HOME → démarre automatiquement sur TV Box.

## Ports réseau

| Port | Protocole | Usage |
|---|---|---|
| 9000 | WebSocket (TCP) | Réception données régie |
| 9002 | UDP broadcast | Auto-découverte régie |
