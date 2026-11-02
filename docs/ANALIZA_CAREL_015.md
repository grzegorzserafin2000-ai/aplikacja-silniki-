# Analiza pakietu CAREL / ZAset i poprawki Android 0.15

## Ustalenia z materiałów

Przeanalizowano przesłany pakiet `ClimaGold_CODEX_ZIEHL_CAREL_pakiet_ponizej_32MB.zip`.
Opis przypadku w pakiecie: i1=0x0100, oznaczenie firmware 133I, fabryczne 247/19200/8E1, docelowe 231 lub 232/9600/8N2; aktywny 231 i zapisany 230 występowały podczas nieudanych prób. Jest to opis obserwacji użytkownika, nie odczyt wykonany w tym środowisku.

`EC_modbus.isaxml` zawiera PouUIProperties jako base64 z czytelnym XML schematu. Odczytano go bez uruchamiania projektu sterownika. PouBody i AcfMlge pozostają w formacie binarnym.

Potwierdzone połączenia w schemacie dla M1:
- bity za_new_parity_m1.0..3 -> za_com_parameter_m1.0..3;
- bity za_new_baudrate_m1.0..3 -> za_com_parameter_m1.4..7;
- bity za_nowy_adres_m1.0..7 -> za_com_parameter_m1.8..15;
- za_wpisz_m1 -> TON_3.IN, czas_1 -> TON_3.PT (klucze 3243->314 i 6325->314);
- TON_3.Q prowadzi do fragmentu zmiany parametrów oraz do zmien_baud;
- literal 3698 -> za_pin_m1 (klucze 372->274);
- za_nadpisz_adres_m1 -> TON_5.IN, czas_2 -> TON_5.PT;
- selektor, konwersje i za_nadpisz_adres_m1 zmieniają lokalny za_adres_m1 na za_nowy_adres_m1.

W EC_modbusVariables.xml wartości początkowe to czas_1=T#5s i czas_2=T#15s. To wartości konfiguracyjne projektu, nie dowód wymaganych czasów silnika ani bieżących wartości pracującego sterownika.
Kod 9095 znajduje się w osobnym fragmencie ustawień fabrycznych. Nie zastosowano go w aplikacji. Nie znaleziono literalnego PIN 1234 w tym schemacie; nie wynika z tego brak kontroli uprawnień w innym komponencie.

EC_SR_modbus ma inną procedurę i nazwy sr_*, bez literalnych 1234/3698 w analizowanym XML UI. Nie przeniesiono jego obsługi na ECblue ani nie rozszerzono listy modeli.

Nie odtworzono kompletnych ramek CAREL: pakiet nie ustala wszystkich powiązań zmiennych z driverem RS485, cyklicznego harmonogramu drivera ani funkcji FC06/FC16. Nie można na podstawie samego grafu udowodnić pełnej sekwencji bajtowej sterownika.

DLL ZAset to skompilowane biblioteki. Odczytane nazwy metadanych obejmują AccessPin, COMParameterBaudrate, WriteHoldingRegister i WriteHoldingRegisters. Same nazwy nie ustalają kolejności operacji, formatu h3 ani wybranej funkcji MODBUS. Biblioteki nie były uruchamiane, dekompilowane ani dołączane do aplikacji.

## Porównanie i znalezione ograniczenia prototypów

Android i Windows już mają sekwencję 1234 -> h3 -> 3698. Robocza notatka z pakietu o braku aktywacji opisuje wcześniejszą wersję, nie obecny kod.
Android wcześniej wykonywał pojedyncze odczyty po zapisie i po krótkim opóźnieniu 260 ms po aktywacji. Windows analogicznie czekał około 250 ms. To zbyt krótka obserwacja, aby odróżnić opóźnione przełączenie od braku aktywacji; nie dowodzi to, że opóźnienie było przyczyną zgłoszonej awarii.
Android nie wymuszał odstępu RTU między kolejnymi żądaniami i czyścił również bufor TX. Brakowało raportu surowych ramek. CRC, kodowanie h3 i dopasowanie odpowiedzi FC06 w dostarczonym kodzie nie wykazały błędu w wykonanych testach.

## Zmiany w Androidzie

- Jedna testowalna maszyna sekwencji CommunicationProgrammer dla przycisku zmiany parametrów.
- Każdy zapis wysyłany najwyżej raz. Timeout nie powoduje retransmisji.
- Po h3 odczyty kontrolne przez maksymalnie około 5 s; bez potwierdzenia wartości nie ma aktywacji.
- Jeśli h3 już zawiera docelowe ustawienia, pomijany jest ponowny zapis i odblokowanie; 3698 idzie na aktywny adres i aktywne parametry portu.
- Po aktywacji odczyty nowego połączenia przez około 15 s. Sukces wymaga zgodnego kodu produktu i h3 na nowym łączu. Czasy to budżety diagnostyczne, nie skopiowana gwarantowana sekwencja CAREL; ostatnie rozpoczęte odczyty mogą zakończyć się po granicy budżetu.
- RtuSession wymusza co najmniej 3,5 znaku ciszy (minimum 4 ms), czyści tylko RX i obsługuje fragmenty ramek oraz długi prefiks zakłóceń.
- Raport zawiera monotoniczne znaczniki czasu, LINK, TX, RX, timeouty i wyjątki. Ograniczenie bufora raportu jest oznaczone przy obcięciu.
- Powtarzane ustawianie identycznych parametrów portu pomijane.
- Wynik potwierdzonej zmiany pokazuje nowy adres na głównym ekranie.

Nie zmieniono listy modeli dopuszczonych do zapisu, nie dodano zgadywanego FC16 i nie usunięto wymogu potwierdzenia PIN 1234 przed nowym zapisem h3. Bez śladu z urządzenia nie wiadomo, czy to właśnie odblokowanie było blokującym etapem.
Kod Windows służył do porównania; nie wydano nowego programu Windows. Logo pozostaje poprzednie: w pakiecie są dotychczasowe zasoby, brak osobnego nowego logo z wiadomości.

## Weryfikacja i granice

`tests/run.sh`: 13 przypadków walidacji adresu oraz 33 asercje komunikacji. Symulacje obejmują zapisany 230 / aktywny 231, utracone ACK, opóźniony zapis i aktywację, natychmiastowe przełączenie, 247/19200/8E1 -> 231/9600/8N2, zmianę formatu przy tym samym adresie, odrzucenie zapisu, nieznany model, zmienione dane, inny kod produktu na celu, CRC, fragmentację, zakłócenia i brak retransmisji.
`assembleDebug`: kompilacja Android 0.15; podpis APK weryfikowany przez apksigner.
Nie wykonano testu na tablecie, adapterze ani fizycznym silniku. Nie ustalono jednoznacznej przyczyny sprzętowego FC06 timeout.

Następny potrzebny dowód: raport po jednej próbie na pojedynczym zatrzymanym silniku testowym odłączonym od magistrali centrali. Raport pozwoli rozróżnić brak ACK dla PIN 1234, zapis h3 bez odczytu, brak aktywacji i problem formatu RS485. Bez takiego wyniku nie należy uznawać rzeczywistej zmiany adresu za potwierdzoną.
