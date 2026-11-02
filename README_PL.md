# Clima Gold Ziehl-Abegg

Nowa, osobna aplikacja z pełnym logo użytkownika i kodowaniem CM=3. Nazwa widoczna na tablecie i w oknie Windows: **Clima Gold Ziehl-Abegg**.

## Pobieranie

- [Tablet Android — APK, osobna aplikacja](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet.apk)
- [Tablet — APK w ZIP](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet.zip)
- [Android — źródła i notebook do budowania](downloads/ClimaGold_ZiehlAbegg_CM3_Zrodla.zip)
- [Windows x64 — przenośny EXE w ZIP](https://github.com/grzegorzserafin2000-ai/aplikacja-silniki-/releases/download/windows-cm3-v1.0.0-test/ClimaGold_CM3_Windows_TEST.zip)
- [Windows — źródła i instalator skrótu](downloads/ClimaGold_CM3_Windows_Zrodla.zip)

ZIP Windows należy rozpakować przed uruchomieniem. EXE nie wymaga Pythona. Instalator źródłowy wymaga Python 3.10+ i internetu przy pierwszej instalacji. [Instrukcja Windows](windows/README_PRZECZYTAJ.txt).

Android ma osobny applicationId `pl.cg.ziehlconfig.cm3`; stara aplikacja `pl.cg.ziehlconfig` pozostaje osobno. Nazwa jest taka sama zgodnie z prośbą użytkownika. Windows używa osobnego katalogu `ClimaGold_ZIEHL_CM3`; instalator chroni istniejący skrót innej wersji.

## Kodowanie

- Czytelny podgląd modelu, firmware, aktywnej/zapisanej komunikacji i CM (odczyt/cel).
- Nawiew 231, Wywiew 232 oraz ręczny adres. Fabryczny profil komunikacji 247/19200/Even/1 przygotowuje formularz; zapis wymaga osobnego potwierdzenia.
- Komunikacja jest weryfikowana na docelowym adresie. Następnie CM w h4 jest ustawiane na 3, z zachowaniem pozostałych bitów. Sukces wymaga odczytu kontrolnego.
- Niepotwierdzone CM daje wynik niepełnego kodowania. Każdy zapis wysyłany raz; bez automatycznych retransmisji.
- Zapis ograniczony do rozpoznanych modeli 0x0100/0x0101. VMAX i pozostałe parametry nie są programowane.

[Procedura, dowody i ograniczenia](docs/CM3_KODOWANIE.md). [Protokół i testy](docs/PROTOKOL_I_TESTY.md). [Przygotowanie logo](branding/README.md).

## Weryfikacja

Android: `JAVA_HOME=<JDK17> tests/run.sh` — 13 przypadków adresu i 48 asercji komunikacji. Budowanie: Gradle 8.9, JDK 17, Android SDK 35, `gradle assembleDebug`.
Windows: z katalogu `windows`, `python -m unittest discover -s tests -v` — 74 testy, w tym GUI. Workflow buduje EXE na Windows i uruchamia gotowy plik w trybie smoke-test.

Działanie z fizycznym silnikiem i USB-RS485 nadal wymaga sprawdzenia. Program pozostaje wersją testową. Po próbie należy skopiować raport techniczny TX/RX.
