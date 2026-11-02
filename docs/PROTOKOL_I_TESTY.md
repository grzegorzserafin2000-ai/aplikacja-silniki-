# MODBUS RTU — Clima Gold CM3 — Android / Windows TEST

Wykrywanie jest tylko odczytem FC03 i FC04 z CRC, adresem i funkcją odpowiedzi. Odczyt i1 identyfikuje model, i0 firmware; h3 opisuje komunikację, h4 tryb sterowania CM. Zapis jest dostępny tylko dla rozpoznanych kodów 0x0100/0x0101 z poprawnym h3 i świeżą weryfikacją danych.

## h3
Bity 15..8: adres 1..247. Bity 7..4: baud code (0=4800, 1=9600, 2=19200, 3=38400, 4=115200). Bity 3..0: 0=8N1, 1=8O1, 2=8E1, 3=8N2. Zapis 38400/115200 nadal zablokowany w tym profilu.
Przykład 231 / 9600 / 8N2: 0xE713.
Nowe h3: PIN 1234 -> jeden FC06 h3 -> odczyt kontrolny. Aktywacja 3698 tylko po potwierdzeniu zapisanego celu; sprawdzenie nowego i1+h3 do około 15 s. Brak automatycznych retransmisji zapisów.

## CM w h4
Po potwierdzeniu docelowego połączenia ustawiane jest CM=3 przez read/modify/write `(h4 & 0xFFF0) | 3`. Pozostałe bity są zachowywane. Wymagany jest osobny odczyt kontrolny h4. Szczegóły dowodów, kolejności i ograniczeń: [CM3_KODOWANIE.md](CM3_KODOWANIE.md).

## Testy
Android: `JAVA_HOME=<JDK17> tests/run.sh` i Gradle assembleDebug.
Windows: z katalogu windows `python -m unittest discover -s tests -v`. Testy GUI wymagają Windows lub dostępnego ekranu X; pominięty test nie jest traktowany jako wykonany. Workflow buduje EXE na Windows i uruchamia go w trybie smoke-test.
Testy symulowane nie potwierdzają działania z fizycznym silnikiem i adapterem RS485.
