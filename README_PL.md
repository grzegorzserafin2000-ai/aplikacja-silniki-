# Clima Gold Ziehl-Abegg

Nowa, osobna aplikacja z pełnym logo użytkownika i kodowaniem CM=3. Nazwa widoczna na tablecie i w oknie Windows: **Clima Gold Ziehl-Abegg**.

## Pobieranie

- [Tablet Android — APK, osobna aplikacja](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet_Kreator_osobna_v121.apk)
- [Tablet — APK w ZIP](downloads/ClimaGold_ZiehlAbegg_CM3_Tablet_Kreator_osobna_v121.zip)
- [Android — źródła i notebook do budowania](downloads/ClimaGold_ZiehlAbegg_CM3_Zrodla_Kreator_osobna_v121.zip)
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
numer centrali (C/K + numer bez limitu długości), typ centrali, liczbę silników, przygotowanie,
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

Poprawka kreatora 1.1.1: osobne przyciski C i K, domyślnie C; komunikat nieprawidłowego numeru pojawia się przy polu numeru, zamiast sugerować ponowny wybór C/K. Numer 12345 jest prawidłowy dla C oraz K.

1.1.2: numer centrali jest ciągiem cyfr bez limitu długości; zachowuje zera wiodące. Wymagana jest przynajmniej jedna cyfra. Pełny numer trafia do treści raportu; tylko proponowana nazwa pliku jest skracana dla zgodności z systemem plików.

## Wygląd tabletu 1.2.0

Duże kafelki menu z ikonami, pasek pięciu kroków, ikony nawiewu/wywiewu,
licznik potwierdzonych silników, ikony przycisków i krótkie przejścia ekranów.
Podczas operacji widoczne są obracający się wentylator i nieoznaczony procentowo
wskaźnik pracy. Kodowanie pokazuje etap otrzymany z istniejącego programatora.
Animacje respektują wyłączenie animacji w Androidzie i zatrzymują się po ukryciu ekranu.
Kolor zielony wymaga istniejącego potwierdzenia odczytu; grafika nie zmienia
reguł zapisu, adresów, zakresów skanowania ani walidacji numeru centrali.
Nazwa, identyfikator osobnej aplikacji, logo i ikona uruchamiania pozostają te same.

1.2.1: końcowy ekran ma przycisk „Zakończ procedurę”, odblokowany tylko po poprawnym końcowym teście i potwierdzonym kodowaniu wszystkich zaplanowanych silników. Zakończenie dodaje wpis do raportu i pokazuje podsumowanie. Konfiguracja zaawansowana ma na górze „Wróć do menu”; ekran silnika w kreatorze ma „Wróć do kreatora”. Powrót jest nieaktywny podczas operacji.


## Kreator standardowy 1.3.0

[Pobierz APK 1.3.0 — uproszczony kreator](downloads/ClimaGold_Kreator_1.3.0.apk) · [SHA-256](downloads/ClimaGold_Kreator_1.3.0.apk.sha256)

Jeden wentylator na ekranie, w stałej kolejności: wszystkie nawiewy, następnie
wszystkie wywiewy. Po włączeniu tylko wskazanego wentylatora przycisk
„Wentylator włączony — rozpocznij” uruchamia wyszukanie, zapis oraz odczyt kontrolny.
Nie trzeba wybierać adresu ani parametrów komunikacji. Po potwierdzonym wyniku
należy wyłączyć zabezpieczenie tego wentylatora i potwierdzić przejście dalej.
Błąd zatrzymuje postęp; ponowna próba wymaga naciśnięcia przycisku.
Przywrócenie ekranu po przerwaniu aplikacji nie uruchamia ponownie zapisu.

Schemat nawiewu i wywiewu podświetla bieżącą pozycję. Obracająca się ikona
podczas operacji oznacza pracę aplikacji, a nie obracanie silnika. Zielone
znaczniki wymagają potwierdzonego wyniku. Animacje respektują ustawienia systemowe
i zatrzymują się po ukryciu ekranu. Na końcu trzeba włączyć wszystkie wentylatory
i wykonać końcowy test, zanim pojawi się możliwość zakończenia procedury.
Raport zachowuje informacje techniczne; konfiguracja zaawansowana pozostaje dostępna.
Wersja testowa — działanie na tablecie i fizycznych silnikach wymaga próby sprzętowej.


## Podgląd raportu 1.3.1

[Pobierz APK 1.3.1 — kreator z podglądem raportu](downloads/ClimaGold_Kreator_1.3.1.apk)

Przycisk „Wyświetl raport” na końcu testu i po zakończeniu procedury otwiera
przewijany raport bezpośrednio w aplikacji. Tekst można zaznaczać i kopiować.
Przycisk „Zapisz jako TXT” w podglądzie uruchamia zapis pliku tylko na żądanie.
Podgląd jest również dostępny przed rozpoczęciem nowej sesji.
