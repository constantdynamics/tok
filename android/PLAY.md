# tok in Google Play — Internal testing (dummyproof stappenplan)

Alles wat geautomatiseerd kon worden, staat klaar in de repo. Hieronder staat precies
wat **jij** nog doet. Reken op ~30–45 min (plus eenmalig een Play-ontwikkelaarsaccount).

## Wat al voor je klaarstaat
- ✅ Compleet Android-project **met Gradle-wrapper** — `./gradlew` werkt meteen, ook zonder
  losse Gradle-installatie.
- ✅ **Release-signing** geregeld: zodra je een keystore maakt, wordt de AAB automatisch
  gesigneerd (zie stap 1–2).
- ✅ **targetSdk 35** (voldoet aan Google Play in 2026), app-icoon en app-naam "tok".
- ✅ **Privacybeleid** live op `https://constantdynamics.github.io/tok/privacy.html`.
- ✅ Kant-en-klare **Console-teksten en -antwoorden** (onderaan dit bestand).

## Eenmalige voorwaarden
- Een **Google Play Developer-account** (~$25 eenmalig): <https://play.google.com/console/signup>
- **Android Studio** geïnstalleerd (om te bouwen). De meegeleverde `./gradlew` kan ook,
  maar je hebt sowieso de **Android SDK** nodig (Android Studio installeert die).

---

## Stap 1 — Keystore maken (eenmalig, ~1 min)
In een terminal:
```bash
cd android
./make-keystore.sh
```
Kies een wachtwoord en vul je naam in. Dit maakt `upload-keystore.jks` + `keystore.properties`.

> 🔐 **Bewaar de keystore én het wachtwoord veilig** (bv. in een wachtwoordmanager).
> Zonder deze kun je later geen updates meer uploaden. Beide staan in `.gitignore`, dus ze
> belanden nooit in git.

## Stap 2 — De release-AAB bouwen (~2–5 min)
```bash
cd android
./gradlew bundleRelease
```
Resultaat: **`android/app/build/outputs/bundle/release/app-release.aab`**

> ⚠️ Het project is in de ontwikkelomgeving **niet gecompileerd** (er was geen Android SDK).
> Lukt de eerste build niet, stuur me dan de foutmelding — meestal is het een kleine
> versie-tweak die ik direct kan oplossen. In Android Studio kun je ook gewoon
> **Build → Generate Signed Bundle/APK** gebruiken.

## Stap 3 — App aanmaken in de Play Console
1. Open <https://play.google.com/console> → **Create app**.
2. App name: **tok** · Default language: **Nederlands** · Type: **App** · **Free**.
3. Vink de verklaringen aan → **Create app**.

## Stap 4 — "App content" invullen (verplicht vóór je kunt testen)
Linkermenu → **Policy → App content**:
- **Privacy policy**: `https://constantdynamics.github.io/tok/privacy.html`
- **Data safety**: gebruik de antwoorden onder *Bijlage A*.
- **Advertenties**: **Nee**.
- **Content rating**: vul de vragenlijst in (geen geweld/seks/drugs → komt uit op *Iedereen/PEGI 3*).
- **Target audience**: kies bv. **18+** (geen kinderen → geen extra vereisten).
- **Government / financial / health apps**: **Nee**.

## Stap 5 — Internal testing-release
1. Linkermenu → **Testing → Internal testing** → **Create new release**.
2. **App signing**: accepteer **Play App Signing** (aanrader; Google beheert de echte
   signing-key, jij uploadt met je upload-keystore).
3. **Upload** `app-release.aab`.
4. Release name: bv. `0.1.0 (1)`. Release notes: zie *Bijlage B*.
5. **Save → Review release → Start rollout to Internal testing**.

## Stap 6 — Jezelf als tester toevoegen
1. Tabblad **Testers** → maak een e-maillijst met **jouw Google-account**.
2. Kopieer de **opt-in-link**, open die op je telefoon, word tester en installeer via Play.

🎉 Klaar! De app staat in je Play-testomgeving.

**Updates later:** verhoog `versionCode` (en eventueel `versionName`) in
`android/app/build.gradle.kts`, draai `./gradlew bundleRelease`, en upload als nieuwe release.

---

## Bijlage A — Data safety-antwoorden (kopieer in de Console)
- **Verzamelt of deelt de app gebruikersdata?**
  - **Audio/spraak:** *niet verzameld* — spraak wordt **lokaal op het toestel** verwerkt
    (Vosk) en niet opgeslagen of verzonden.
  - **Notities (bullets/labels):** opgeslagen in een **Supabase**-database t.b.v. de werking
    en synchronisatie tussen de apparaten van de gebruiker. Categorie: *App activity /
    overige door de gebruiker gegenereerde inhoud*. **Niet gedeeld** met derden, **niet verkocht**.
  - Geen advertenties, geen analytics/tracking, geen locatie, geen contacten, geen
    identifiers.
- **Versleuteld tijdens verzending?** **Ja** (HTTPS).
- **Kunnen gebruikers om verwijdering vragen?** **Ja** — in de app verwijderen + apparaat
  ontkoppelen; contact via het privacybeleid.

## Bijlage B — Release notes (NL)
```
Eerste interne testversie van tok: spreek je gedachten in en bewaar ze als
gelabelde bullets, met live sync naar de webpagina. Offline spraakherkenning (NL).
```

## Belangrijk
- **`applicationId` = `app.tok`** is permanent na de eerste upload. Wil je iets anders, pas
  dat dan **nu** aan in `android/app/build.gradle.kts` (`applicationId` én `namespace`).
- Vervang in `web/privacy.html` het contact-e-mailadres door het jouwe (1 regel).
