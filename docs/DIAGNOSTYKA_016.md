# Wersja 0.16: raport urządzenia i sprawdzenie trybu transmisji

## Dane z przesłanego raportu 0.15
Silnik i1=0x0100, firmware i0=1331; adapter USB VID=1027, PID=24577 (FTDI).
Wykryte połączenie 231/9600/8E1, podczas gdy h3=0xE713 oznacza 231/9600/8N2.
Próba docelowa: 232/9600/8N2, h3=0xE813.
- FC06 h0=1234: odpowiedź identyczna z żądaniem; może być ACK lub echo.
- FC06 h3=0xE813: brak odpowiedzi.
- Kolejne FC03 h3: wciąż 0xE713, prawidłowy CRC.
- Brak odpowiedzi pod 232/9600/8N2.
- Aplikacja prawidłowo nie wysłała 3698, ponieważ nowego h3 nie potwierdzono.

Zatem zapis nie został potwierdzony; samo wydłużenie czekania na aktywację nie rozwiązuje tego przypadku.

## Weryfikacja bibliotek ZAset
Odczyt instrukcji IL z dostarczonych bibliotek służył ustaleniu sekwencji wywołań. Biblioteki i ich kod nie są dołączane do repozytorium.
- clsECblue.set_COMParameterAdress w ZAset.Data.Convert.dll ustawia h3 przez set_SendRegister; kodowanie adresu zajmuje bity 15..8.
- set_SendRegister tworzy clsWriteFunctionPropertys, którego wewnętrzny FunctionCode domyślnie wynosi 16.
- Jednak WriteData w ZAset.CommunicationServer.dll dla tablicy z jednym słowem wywołuje WriteSingleRegister.
- ModbusMaster.WriteSingleRegister w ZA.Development.ModbusBase.dll faktycznie dodaje do ramki kod 6.

Wewnętrzna wartość 16 NIE dowodzi FC16 na przewodzie. Wstępna hipoteza o konieczności zmiany FC06 na FC16 została odrzucona po sprawdzeniu całej ścieżki. Wydanie 0.16 zachowuje FC06.

## Zmiany 0.16
1. Przy różnicy trybu aktywnego i zapisanego, jeśli zapisany adres jest nadal tym samym adresem, odczyt i1+h3 sprawdza zapisany tryb. Jest wybierany tylko po zgodnym odczycie obu rejestrów. Gdy nie odpowiada, oryginalne połączenie jest ponownie sprawdzane przed zapisem.
2. Zapisany inny adres oczekujący na aktywację nie zastępuje aktywnego adresu do zapisu.
3. Po zmianie parametrów USB dodano 100 ms na ustabilizowanie konfiguracji portu.
4. Po odpowiedzi na PIN 1234 dodano 500 ms przed h3. To środek kompatybilności, nie udowodniony wymagany czas firmware.
5. Raport pokazuje wybrany format, ACK/timeout h3 i kolejne surowe wartości h3. Komunikaty READ OK i FC06 ACK są rozdzielone.
6. Bez ponawiania zapisu i bez aktywacji po niepotwierdzonym h3; lista modeli nie została rozszerzona.

## Testy i ograniczenia
13 przypadków walidacji adresu, 38 asercji komunikacji; nowe scenariusze modelują odczyty działające w E1 przy zapisie wymagającym N2, wybór potwierdzonego trybu, powrót do oryginalnego gdy zapisany nie odpowiada i odstęp po PIN.
To symulacja hipotezy o rozbieżności formatu, a nie dowód zachowania fizycznego urządzenia. Przyczyna braku odpowiedzi FC06 h3 nadal wymaga wyniku z urządzenia.
Kompilacja i podpis APK sprawdzane lokalnie. Potrzebny jest raport po próbie 0.16 na pojedynczym silniku testowym. Jeśli h3 nadal pozostaje 0xE713, raport pokaże, czy próba odbyła się w potwierdzonym N2 czy w E1; kolejnym rozstrzygającym materiałem będzie ślad udanej operacji z ZAset/Carel.
Logo pozostaje dotychczasowe.
