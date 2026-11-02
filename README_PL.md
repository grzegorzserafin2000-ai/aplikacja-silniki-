# Clima Gold Ziehl-Abegg

Nowa, osobna aplikacja z pełnym logo użytkownika i kodowaniem CM=3. Nazwa widoczna na tablecie i w oknie Windows: **Clima Gold Ziehl-Abegg**.

## Pobieranie

- [Tablet Android — APK, osobna aplikacja](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet_Kreator_osobna_v110.apk)
- [Tablet — APK w ZIP](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet_Kreator_osobna_v110.zip)
- [Android — źródła i notebook do budowania](downloads/ClimaGold_ZiehlAbegg_CM3_Zrodla_Kreator_osobna_v110.zip)
- [Windows x64 — przenośny EXE w ZIP](https://github.com/grzegorzserafin2000-ai/aplikacja-silniki-/releases/download/windows-cm3-v1.0.3-test/ClimaGold_CM3_Windows_TEST.zip)
- [Windows — źródła i instalator skrótu](downloads/ClimaGold_CM3_Windows_Zrodla_v103.zip)

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
Windows: z katalogu `windows`, `python -m unittest discover -s tests -v` — 78 testów, w tym GUI. Workflow buduje EXE na Windows i uruchamia gotowy plik w trybie smoke-test.

Działanie z fizycznym silnikiem i USB-RS485 nadal wymaga sprawdzenia. Program pozostaje wersją testową. Po próbie należy skopiować raport techniczny TX/RX.

Wersja 1.0.1 przywraca wcześniejszą ikonę i dopasowuje logo do jednolitego, jaśniejszego tła aplikacji #263647. Zmieniono wyłącznie wygląd; nazwa i obsługa CM pozostają takie same.

Wygląd 1.0.2: czarne tło (#000000), logo dopasowane do czerni, widoczne bursztynowe podświetlenie naciskanych przycisków. Windows: uruchom `ClimaGold_ZiehlAbegg_102.exe` z rozpakowanego ZIP. Nowa nazwa EXE i osobny plik ciemnej ikony ograniczają wpływ starej ikony z pamięci podręcznej Windows.

Skanowanie 1.0.3: domyślnie adresy od 247 w dół do 200, kolejno 19200/8E1, 9600/8N2, 9600/8E1, 19200/8N2. Pierwsza odpowiedź kończy skan. Wszystkie adresy 1–247 wymagają zaznaczenia pełnego skanowania w Więcej ustawień. Bez odpowiedzi cztery profile mogą zająć około 3 minut; można przerwać skan.

## Tablet 1.1.0 — kreator ZIEHL-ABEGG

Menu zawiera konfigurację standardową i zaawansowaną. Standardowa prowadzi przez
numer centrali (C/K + 4–5 cyfr), typ centrali, liczbę silników, przygotowanie,
kodowanie kolejnych silników i końcowy test. Do 8 silników na każdą sekcję:
nawiew 231,233,…245; wywiew 232,234,…246. Adres 247 pozostaje dostępny ręcznie.
Każdy silnik należy najpierw wyszukać i potwierdzić kodowanie; wynik jest zaliczany
wyłącznie po odczycie kontrolnym adresu, transmisji i CM. Parametry: 9600/8N2, CM=3.

Postęp jest zapisywany na tablecie, można wznowić sesję i wyeksportować raport TXT.
Końcowy test całej centrali wykonuje wyłącznie odczyty. Ponowne uruchomienie aplikacji
wymaga ponownego końcowego testu, zanim pokaże ona pełne potwierdzenie.
Zaawansowana zachowuje ręczne parametry, wyszukiwanie i przygotowanie komunikacji fabrycznej.
To wersja testowa: kompilacja, lint i testy symulowanego protokołu przeszły;
interfejs i rzeczywiste kodowanie wymagają próby na tablecie z silnikiem.

Kreator 1.1.0 jest teraz osobną aplikacją (`pl.cg.ziehlconfig.wizard`), instalowaną obok poprzednich wersji `.cm3` i aplikacji oryginalnej. Zachowuje nazwę „Clima Gold Ziehl-Abegg”, logo i ikonę. Nie przenosi danych poprzedniej aplikacji.
