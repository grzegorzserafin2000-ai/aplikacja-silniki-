# Clima Gold – ZIEHL-ABEGG Android 0.16 TEST

## Pobieranie aplikacji

- [Pobierz APK 0.16 TEST](downloads/ClimaGold_016.apk)
- [Pobierz APK w ZIP](downloads/ClimaGold_016_APK.zip)
- [Źródła i notebook do budowania](downloads/ClimaGold_016_Zrodla_i_notebook.zip)

Na stronie pliku GitHub wybierz **Download raw file** (ikona pobierania). Całe repozytorium można pobrać przez **Code → Download ZIP**.

APK pozostaje wersją testową. Kompilacja i testy symulowane przeszły; zmiana adresu na fizycznym silniku wymaga sprawdzenia.

Źródła odzyskane z przesłanego notebooka v15; poprawki interfejsu i obsługi komunikacji dotyczą wersji 0.16.

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
Z JDK 17 uruchom `tests/run.sh`: 13 przypadków walidacji adresu i 38 asercji symulowanej komunikacji.

## Poprawka po raporcie z silnika
Wersja 0.16 sprawdza potwierdzony tryb z h3 przed zapisem i dodaje odstępy po zmianie konfiguracji USB oraz PIN. FC06 zachowany po weryfikacji pełnej ścieżki ZAset. [Szczegóły i ograniczenia](docs/DIAGNOSTYKA_016.md).

## Zmiany komunikacji
Odstępy RTU, raport TX/RX, odczyty kontrolne przez około 5/15 s i obsługa zapisanych ustawień różniących się od aktywnego adresu. Każdy zapis wysyłany raz. Sukces wymaga odczytu kodu produktu i h3 na docelowym połączeniu.
Szczegółowa analiza i ograniczenia: [docs/ANALIZA_CAREL_015.md](docs/ANALIZA_CAREL_015.md).

## Do sprawdzenia na urządzeniu
Nie wykonano próby na tablecie ani silniku. Potrzebny jest raport techniczny po jednej próbie na silniku testowym. APK pozostaje testowe.
Nie rozszerzono listy obsługiwanych modeli. Logo pozostaje dotychczasowe, ponieważ pakiet nie zawiera nowego pliku PNG/SVG z wiadomości.
APK 0.16 ma ten sam podpis co wydane tutaj 0.14 i 0.15; można je aktualizować bez odinstalowywania.


## Windows 2.6 TEST

- [Pakiet źródłowy z instalatorem i skrótem na pulpicie](downloads/ClimaGold_Windows_2_6_TEST_Zrodla.zip) — wymaga Python 3.10+ i internetu przy pierwszej instalacji.
- [Przenośny EXE dla Windows x64](https://github.com/grzegorzserafin2000-ai/aplikacja-silniki-/releases/download/windows-v2.6.0-test/ClimaGold_ZIEHL_Windows_2_6_TEST.zip) — zbudowany na Windows i sprawdzony testem uruchomienia; ZIP należy rozpakować przed uruchomieniem. Nie wymaga Pythona. [Weryfikacja](docs/WINDOWS_26_WERYFIKACJA.md).

Uruchom `ZAINSTALUJ_NA_PULPICIE.vbs` z rozpakowanego pakietu źródłowego. Szczegóły w [windows/README_PRZECZYTAJ.txt](windows/README_PRZECZYTAJ.txt). Kod Windows ma 61 testów, w tym testy okna i edycji adresu. Zapis na fizycznym silniku pozostaje niezweryfikowany.
