# Odczyt i zapis – MODBUS RTU

## Wykrywanie
Skaner wysyła **wyłącznie** polecenie FC03: Read Holding Register, rejestr `h3` (adres PDU `0x0003`) po kolei na adresy 1–247. Najpierw najczęściej spotykane adresy (247/221/231/1) na kilku konfiguracjach portu, następnie pełny skan. Wszystkie odpowiedzi są sprawdzane przez CRC16 i zgodność adresu.

Rejestr `h3` może być niedostępny w niektórych silnikach. Jeśli silnik odpowie prawidłową ramką wyjątku MODBUS (FC83), aplikacja pokaże, że urządzenie odpowiedziało, ale zablokuje zapis.

## Struktura COM Parameter h3 z dokumentu L-BAL-E076-GB
- Bity 15–8: adres jednostki (1–247).
- Bity 7–4: kod prędkości 0=4800, 1=9600, 2=19200, 3=38400 (nie każdy silnik), 4=115200 (nie każdy silnik).
- Bity 3–0: 0=8N1, 1=8O1, 2=8E1, 3=8N2 (nie każdy silnik).

Przykład: 231, 9600, 8N2 → `(231 << 8) | (1 << 4) | 3` = `0xE713`.

## Zapis – TYLKO po potwierdzeniu profilu
1. Ponownie czytaj `h3` przez stary aktywny adres i format. Wartość musi być zgodna z odczytem wykrywania.
2. Zapisz PIN 1234 do `h0` (FC06) w celu uzyskania poziomu 1.
3. Zapisz nowe słowo do `h3` (FC06).
4. Natychmiast odczytaj `h3` (FC03) i porównaj z oczekiwaną wartością.
5. Użytkownik wykonuje bezpieczny restart sprzętowy; skanuj ponownie.

Brak automatycznego restartu i brak automatycznego zatwierdzania PIN 3698. Dla innych modeli aplikacja pozostaje tylko do odczytu.

## Ograniczenia
- Brak automatycznej identyfikacji modelu po numerze typu.
- Sterowniki USB zależą od konwertera. Biblioteka nie obsługuje każdego kabla.
- Oprogramowanie wymaga testu na fizycznym silniku i Androidzie, szczególnie przy przerwach transmisji, błędnej parzystości i rozbieżności aktywnego/zapisanego adresu.
- Testy `ProtocolSelfTest.java` sprawdzają ramki i CRC, **nie** fizyczną komunikację, nie są testem zgodności z nowymi modelami.
