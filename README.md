# Home Switch (Fabric, Minecraft 1.21.4)

Mod kliencki. Gdy inny gracz wejdzie na chunk, na którym stoisz, mod wpisuje `/home 2`
(jeśli jesteś na home 1) albo `/home 1` (jeśli jesteś na home 2).

## Klawisze (zmienisz w Ustawienia → Sterowanie → Home Switch)
- **K** – włącz / wyłącz mod (po włączeniu zakłada, że jesteś na home 1)
- **J** – ręcznie przestaw, na którym home aktualnie jesteś (1 ↔ 2)

## Działa po zminimalizowaniu
Mod wyłącza `pauseOnLostFocus`, więc gra dalej tickuje, gdy okno jest zminimalizowane
lub nieaktywne (na serwerze multiplayer).

## Ustawienia
Na górze `HomeSwitchClient.java`: format komendy, cooldown, lista ignorowanych graczy.

## Budowanie
- Lokalnie: Java 21 + `gradle build` → jar w `build/libs/`
- Automatycznie: GitHub Actions (zakładka Actions → build → Artifacts)

Wymaga Fabric Loader ≥ 0.16 i Fabric API dla 1.21.4.
