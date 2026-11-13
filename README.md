# KosaVisuals (Fabric 1.21.4, client-side)

Prawy Shift otwiera menu z kolumnami modułów (przeciągaj panele za nagłówek, PPM / strzałka = ustawienia, pole wyszukiwania).
**L** w menu otwiera edytor HUD. Przycisk **Configi** jest pod wyszukiwarką.

## Moduły
- **PVP:** Hitbox, **TargetHUD** (kolor + gradient), **Target ESP** (pierścień, okrąg pod nogami albo **Duszki** –
  dwa świecące kółka ze smugą krążące wokół gracza; kolor do wyboru), **Kill Effect** (tryb, czas ducha, wysokość wyrzutu,
  kolor ducha, kolor pioruna), **Damage Numbers** (rozmiar, kolor).
- **Dźwięki:** Hit Sound, Kill Sound, Click Sound – m.in. **Mau (mały kotek)**, Mau (kot), Miau, Mrrau.
  Własne .wav: przeciągnij na okno gry przy otwartym menu albo wrzuć do `config/holovis/sounds`.
- **Świat:** Custom Sky (gotowe nieba albo własne kolory), **Motion Blur** (moc 0–200), **Kolorki** (korekcja kolorów gry:
  style Żywe / Filmowe / Ciepłe / Chłodne / Pastelowe / Soczyste + suwaki nasycenia, kontrastu, jasności i ciepła).
- **HUD:** Watermark (nazwa, FPS, koordynaty, kolor + gradient), Spotify (kolor + gradient), Cooldowns.
- **Klient:** Akcent GUI (kolor, gradient, tło, kolor napisu, efekt hologramu) oraz **Rozmycie klienta** (0–100):
  rozmywa grę za menu, żeby widać było tylko samo GUI.

Wybór koloru: kliknij wiersz z kolorem – obok panelu otwiera się okienko z kółkiem, paletą i suwakami nasycenia i jasności.

## Edytor HUD (klawisz L)
- Przeciągnij element, żeby go przesunąć.
- Kropki w rogach zmieniają rozmiar (albo kółko myszy nad elementem). Dotyczy Watermarku, Spotify, TargetHUD i Cooldowns.
- Po najechaniu na **Spotify** pojawia się karta z tekstem piosenki – jej rozmiar zmienia prawy dolny róg karty.
- `R` – reset pozycji i rozmiarów, `L` / `ESC` – powrót.

## Configi
Przycisk **Configi** otwiera okno z profilami: nazwa + **Zapisz**, zakładki Ostatnie / Najczęściej / Wszystkie, gwiazdka
(ulubione), **Kod** (kopiuje krótki kod profilu), **x** (usuwa), a na dole **Cofnij**, **Kopiuj kod**, **Wklej kod**, **Domyślne**.
Kod `KV1:...` zapisuje tylko to, co zmieniłeś względem ustawień domyślnych, więc jest krótki. Stare kody `HV1:...` nadal działają.

## Komendy
- `/kosa` albo `/kosa toggle` – otwiera menu
- `/kosa clickgui <klawisz>` – ustawia klawisz otwierający menu, np. `/kosa clickgui Insert` (bez klawisza pokazuje obecny)
- `/kosa config` – okno z profilami
- `/kosa config load <kod albo nazwa profilu>` – wczytuje config
- `/kosa config save <nazwa>`, `/kosa config copy`, `/kosa config list`
- `/kosa ghost` / `/kosa ghost remove` – duch „TestBot” widoczny tylko dla Ciebie
- `/kosa killtest` – podgląd Kill Effectu

## Spotify
Windows 10/11: odczyt i sterowanie przez systemową sesję multimediów (bez logowania).
Otwórz czat i najedź na element Spotify: tekst piosenki (lrclib.net), pasek przewijania i przyciski.
Skróty (Sterowanie → KosaVisuals): `[` poprzedni, `]` następny, `\` pauza/play.

## Uwaga przy aktualizacji
Plik moda nazywa się `KosaVisuals-1.2.0.jar`. Usuń ze `mods` starsze `KosaVisuals-*.jar` / `holovis-*.jar`.
