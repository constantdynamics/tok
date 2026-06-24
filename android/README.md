# tok — Android-app (Ronde 2)

Offline **spraak-naar-bullets** app: spreek in, krijg losse bullets, label/filter/sorteer
ze, en synchroniseer live met de webpagina via hetzelfde Supabase-project. Spraak­herkenning
draait **volledig offline** met [Vosk](https://alphacephei.com/vosk/) (Nederlands).

> Let op: dit project is in de ontwikkelomgeving **niet gecompileerd** (geen Android SDK
> beschikbaar). De code is compleet en consistent, maar bij de eerste **Gradle-sync** kan
> Android Studio kleine versie-aanpassingen voorstellen — accepteer die suggesties.

## Vereisten
- **Android Studio** (recent, bv. Ladybug of nieuwer)
- **JDK 17**
- **Android SDK 34** (compileSdk/targetSdk 34, minSdk 26)
- Een **fysiek toestel** met microfoon (aanrader) of een emulator met microfoon

## Bouwen & draaien
1. Open de map **`android/`** in Android Studio (*File → Open*).
2. Laat Gradle synchroniseren. Android Studio zet automatisch de **Gradle-wrapper** op.
3. Kies je toestel en druk **Run** (▶). Bij de eerste keer vraagt de app microfoon­toegang.

CLI-alternatief (vereist een lokale Gradle 8.7+ om de wrapper te genereren):
```bash
cd android
gradle wrapper           # eenmalig: maakt gradlew + wrapper-jar
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
```

## Eerste start
1. **Koppelen.** Genereer een 6-cijferige code op de webpagina (knop **Koppel**) of op
   een ander gekoppeld apparaat (Instellingen → *Nieuwe koppelcode*), en voer 'm in.
   (Of zaai er eentje in de Supabase SQL-editor; zie de hoofd-`README`.)
2. **Microfoon** toestaan.
3. **Spraakmodel.** Bij de eerste opname downloadt de app eenmalig het **kleine** NL-model
   (~40 MB) naar de app-opslag. Daarna werkt opnemen **offline en direct**. Het **grote**
   model (~1,4 GB, nauwkeuriger) is optioneel in **Instellingen** — opname start altijd
   met klein en schakelt bij een pauze over zodra groot geladen is.

Modellen kun je ook **importeren** (Instellingen → *Importeren*) als je de Vosk-zip al
hebt: [klein](https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip) ·
[groot](https://alphacephei.com/vosk/models/vosk-model-nl-0.22.zip).

## Distributie via Google Play (Internal testing)
1. Maak een **keystore** en voeg een `signingConfig` voor `release` toe in
   `app/build.gradle.kts` (niet meegeleverd — houd je keystore privé).
2. `./gradlew bundleRelease` → AAB in `app/build/outputs/bundle/release/`.
3. Upload in de **Play Console** onder **Internal testing**, voeg jezelf als tester toe.
   `applicationId` is `app.tok` (pas aan als gewenst vóór de eerste upload).

## Architectuur
- **UI**: Jetpack Compose (Material 3, donker neon-thema). Schermen: Opnemen, Overzicht,
  Labels, Instellingen + Koppelscherm. Eenvoudige `ViewModel`s per scherm.
- **Opslag**: Room als offline-first bron van waarheid (`bullets`, `labels`,
  `bullet_labels` met `dirty`/`deleted`-vlaggen). DataStore voor token + instellingen.
- **Sync**: OkHttp + kotlinx.serialization naar de Supabase REST-API met
  `x-pairing-token`. Push lokale wijzigingen → pull + reconcile (last-write-wins op
  `updatedAt`). Live via een Realtime **broadcast**-nudge + poll-fallback + periodieke
  WorkManager-sync.
- **Spraak**: Vosk met gelaagd modelbeheer (klein→groot) en een voorgrond-service zodat
  opnemen doorloopt met het scherm uit.
- **DI**: handmatige `ServiceLocator` (geen Hilt).

## Mappen
```
android/app/src/main/java/app/tok/
  data/local      Room (entities, DAO's, database)
  data/remote     Supabase REST + Realtime-client + DTO's + config
  data/repo       TokRepository (offline-first sync)
  data/prefs      DataStore
  speech          Vosk modelbeheer + herkenning + voorgrond-service
  sync            WorkManager
  ui/...          Compose-schermen + thema + componenten
  di              ServiceLocator
```
