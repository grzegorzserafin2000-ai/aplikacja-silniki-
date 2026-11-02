# Clima Gold – ZIEHL-ABEGG Android 0.15 TEST

## Pobieranie aplikacji

- [Pobierz APK 0.15 TEST](downloads/ClimaGold_015.apk)
- [Pobierz APK w ZIP](downloads/ClimaGold_015_APK.zip)
- [Źródła i notebook do budowania](downloads/ClimaGold_015_Zrodla_i_notebook.zip)

Na stronie pliku GitHub wybierz **Download raw file** (ikona pobierania). Całe repozytorium można pobrać przez **Code → Download ZIP**.

APK pozostaje wersją testową. Kompilacja i testy symulowane przeszły; zmiana adresu na fizycznym silniku wymaga sprawdzenia.

Źródła odzyskane z przesłanego notebooka v15; poprawki interfejsu i obsługi komunikacji dotyczą wersji 0.15.

- Pole nowego adresu jest zawsze widoczne i przyjmuje wartości 1–247.
- Zapis używa wpisanego adresu także po wybraniu skrótu Nawiew/Wywiew.
- Wykrycie wypełnia adres i parametry aktywnego połączenia; odczyt silnika i kopiowanie raportu są na ekranie głównym.
- Blokada zapisu pokazuje osobno brak obsługi modelu lub niepotwierdzony rejestr h3.
- Biblioteka USB 3.10.0 jest dołączona ze źródeł upstream, bez zależności od JitPack.

## Budowanie
JDK 17, Gradle 8.9, Android SDK 35. Ustaw ANDROID_HOME na katalog SDK.
W katalogu projektu uruchom `gradle --no-daemon --max-workers=2 assembleDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Testy
Z JDK 17 uruchom `tests/run.sh`: 13 przypadków walidacji adresu i 33 asercje symulowanej komunikacji.

## Zmiany komunikacji
Odstępy RTU, raport TX/RX, odczyty kontrolne przez około 5/15 s i obsługa zapisanych ustawień różniących się od aktywnego adresu. Każdy zapis wysyłany raz. Sukces wymaga odczytu kodu produktu i h3 na docelowym połączeniu.
Szczegółowa analiza i ograniczenia: [docs/ANALIZA_CAREL_015.md](docs/ANALIZA_CAREL_015.md).

## Do sprawdzenia na urządzeniu
Nie wykonano próby na tablecie ani silniku. Potrzebny jest raport techniczny po jednej próbie na silniku testowym. APK pozostaje testowe.
Nie rozszerzono listy obsługiwanych modeli. Logo pozostaje dotychczasowe, ponieważ pakiet nie zawiera nowego pliku PNG/SVG z wiadomości.
Nowy APK ma ten sam podpis debug co wydana w tej sesji wersja 0.14, ale inny niż przesłana wersja 0.13.
