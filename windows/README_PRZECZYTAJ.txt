CLIMA GOLD - ZIEHL-ABEGG WINDOWS 2.6 TEST

WERSJA PRZENOSNA EXE (bez Pythona):
Pobierz ZIP z wydania windows-v2.6.0-test na GitHub, rozpakuj calosc i uruchom
ClimaGold_ZIEHL_Windows_2_6_TEST.exe. Nie uruchamiaj go bezposrednio wewnatrz ZIP.
Program nie otwiera czarnej konsoli. EXE jest niepodpisane certyfikatem wydawcy.

PAKIET ZRODLOWY / INSTALACJA ZE SKROTEM:
Wymaga Windows 10/11, Python 3.10+ z tkinter oraz internetu na pierwsza instalacje.
Rozpakuj ZIP i uruchom ZAINSTALUJ_NA_PULPICIE.vbs.
Instalator przygotuje izolowane srodowisko i skrot na pulpicie, bez praw administratora.
Aplikacja instaluje sie w LOCALAPPDATA\Programs\ClimaGold_ZIEHL.
Nie usuwa ani nie odinstalowuje starszej aplikacji. Aktualizuje jej skrot.
Sterownik USB-RS485 musi byc zainstalowany; port powinien byc widoczny w Menedzerze urzadzen.

ZMIANY 2.6:
- Zawsze widoczne pole adresu. Wpisana liczba ma pierwszenstwo po wybraniu Nawiew/Wywiew.
- Szczegoly silnika, aktywna i zapisana komunikacja oraz firmware na glownym ekranie.
- Kopiowanie i zapis raportu TXT z ramkami TX/RX.
- Sprawdzenie formatu z h3 przed zapisem, jak w Androidzie 0.16.
- Kazdy zapis wysylany raz, odczyty kontrolne do okolo 5/15 s; brak sukcesu na podstawie samego ACK.
- Nadal tylko modele 0x0100 i 0x0101 z poprawnym h3. FC06 zachowany.

WAZNE:
Nie potwierdzono rozwiazania problemu zapisu na rzeczywistym silniku.
Testy programowe i uruchomienie GUI nie zastapia proby na urzadzeniu.
Po jednej probie na pojedynczym silniku testowym skopiuj raport techniczny.
Logo pozostaje dotychczasowe. Nie dolaczono bibliotek producenta ZAset.

BUDOWANIE EXE:
Uruchom OPCJONALNIE_Zbuduj_EXE_na_Windows.bat.
GitHub Actions buduje ten sam program na Windows i sprawdza uruchomienie gotowego EXE.
