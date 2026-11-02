# CM=3: Clima Gold CM3 — Android / Windows TEST

## Ustalenie
W przesłanym projekcie Carel EC_modbus wartość za_nowy_cm_m1 ma wartość początkową 3. Schemat kopiuje bity .0..3 tej zmiennej do za_controlmode_m1.0..3 (klucze połączeń 67->71, 76->77, 78->79, 80->81).
Dostarczona biblioteka ZAset.Data.Convert.dll, clsECblue.get/set_ControlModeSpeedControlMode, operuje na czterech najmłodszych bitach holding register h4, zachowując pozostałe bity przy zmianie. Nie dołączono bibliotek producenta ani ich kodu do wydania.

Poprzednie wydania konfigurowały wyłącznie h3. Nie ustawiały CM. Sama poprawna komunikacja nie zapewnia więc CM 3/3 na Carel.

## Nowa procedura
1. Znany model 0x0100 lub 0x0101, aktualne i1 oraz h3; bez rozszerzania listy modeli.
2. Dotychczasowa zmiana i aktywacja komunikacji, jeśli potrzebna; każdy zapis wysyłany raz.
3. Potwierdzenie i1+h3 na docelowym adresie i formacie, zanim rozpocznie się etap CM.
4. Odczyt h4. Docelowe słowo: `(h4 & 0xFFF0) | 3`.
5. Gdy CM już jest 3, bez zapisu h4. Gdy CM jest inne: PIN 1234, 500 ms, ponowny odczyt h4; zmiana słowa w międzyczasie wstrzymuje zapis.
6. Jeden FC06 do h4 na potwierdzonym docelowym połączeniu. Po niepewnej odpowiedzi wyłącznie odczyty do około 5 s, bez retransmisji.
7. Sukces wymaga zgodności całego słowa h4 z wyliczoną wartością oraz kontroli komunikacji. Brak ACK sam w sobie nie oznacza sukcesu ani konieczności powtórzenia.
8. h4 nie dostaje komendy 3698 ani resetu; 3698 pozostaje wyłącznie aktywacją komunikacji h3.

Etap CM wykonuje się także, gdy adres i komunikacja są już poprawne. Nie zapisuje wtedy ponownie h3. Po niepotwierdzonym CM wynik całego kodowania jest niepełny. Odczyt i cel CM są widoczne na karcie silnika.
VMAX, zadana prędkość, bus enable i pozostałe bity h4 nie są ustawiane przez tę zmianę. To nie jest pełny reset fabryczny.

## Weryfikacja
Android: 13 przypadków walidacji adresu i 48 asercji komunikacji, assembleDebug. Windows: 74 testy, w tym GUI. Testy obejmują zachowanie pozostałych bitów, samo CM bez zmiany adresu, zapis na nowy adres, brak h4, wyjątek, ACK bez zmiany, zmianę innych flag przed zapisem i utratę ACK bez retransmisji.
Przygotowano natywną kompilację EXE z testem uruchomienia w GitHub Actions.
Nie przeprowadzono próby na fizycznym silniku ani odczytu wyświetlacza Carel po zmianie. CM 3/3 w wyniku aplikacji oznacza potwierdzony odczyt bitów 0..3 h4, nie gwarancję odświeżenia ekranu sterownika.

## Logo i osobna aplikacja
Pełne logo pochodzi z Logo_ClimaGold_duże.zip, PNG 40363×12143 px. Zachowano oryginał w branding/logo-original.png; zasoby aplikacji to techniczne eksporty tego rysunku, bez odtwarzania znaku.
Nazwa widoczna: Clima Gold Ziehl-Abegg. Android: osobny applicationId pl.cg.ziehlconfig.cm3, osobna akcja uprawnień USB. Stara aplikacja pozostaje oddzielna. Windows: osobny katalog ClimaGold_ZIEHL_CM3; skrót ma żądaną nazwę, a kolizja z inną aplikacją jest obsługiwana przez osobny folder na pulpicie.
