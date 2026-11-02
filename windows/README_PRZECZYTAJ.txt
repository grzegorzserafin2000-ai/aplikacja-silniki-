CLIMA GOLD - ZIEHL-ABEGG CM3 WINDOWS TEST

WERSJA PRZENOSNA EXE (bez Pythona):
Pobierz ZIP z wydania windows-cm3-v1.0.2-test na GitHub, rozpakuj calosc i uruchom
ClimaGold_ZiehlAbegg_102.exe. Nie uruchamiaj go bezposrednio wewnatrz ZIP.
Program nie otwiera czarnej konsoli. EXE jest niepodpisane certyfikatem wydawcy.

PAKIET ZRODLOWY / INSTALACJA ZE SKROTEM:
Wymaga Windows 10/11, Python 3.10+ z tkinter oraz internetu na pierwsza instalacje.
Rozpakuj ZIP i uruchom ZAINSTALUJ_NA_PULPICIE.vbs.
Instalator przygotuje izolowane srodowisko i skrot na pulpicie, bez praw administratora.
Aplikacja instaluje sie w LOCALAPPDATA\Programs\ClimaGold_ZIEHL_CM3.
Tworzy skrot Clima Gold Ziehl-Abegg. Przy kolizji nazwy uzywa osobnego folderu na pulpicie. Nie aktualizuje skrotu ani plikow starej aplikacji.
Sterownik USB-RS485 musi byc zainstalowany; port powinien byc widoczny w Menedzerze urzadzen.

FUNKCJE WERSJI CM3:
- Kodowanie ustawia CM=3 (mlodsze 4 bity h4), zachowuje pozostale bity i sprawdza odczyt.
- Sukces obejmuje komunikacje i CM. Brak potwierdzenia CM oznacza kodowanie niepelne.
- Przywrocono widoczny przycisk USTAWIENIA FABRYCZNE: 247 / 19200 / Even / 1.
  Przycisk wypelnia pola; zapis nastepuje dopiero po wybraniu ZMIEN PARAMETRY.
  Nie jest to pelny reset fabryczny silnika.
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
Osadzono pelne logo dostarczone przez uzytkownika. Nazwa aplikacji: Clima Gold Ziehl-Abegg. Nie dolaczono bibliotek producenta ZAset.

BUDOWANIE EXE:
Uruchom OPCJONALNIE_Zbuduj_EXE_na_Windows.bat.
GitHub Actions buduje ten sam program na Windows i sprawdza uruchomienie gotowego EXE.

WYGLAD 1.0.1: przywrocono wczesniejsza ikone, naglowek logo ma jednolite tlo aplikacji #263647. Interfejs ma jasniejsze panele. Nazwa oraz osobna instalacja pozostaja takie same.

WYGLAD 1.0.2: czarne tlo aplikacji i logo. Przyciski rozjasniaja sie podczas naciskania oraz przez chwile po kliknieciu. Plik EXE ma nowa nazwe, aby uniknac starej ikony zapamietanej przez Windows. Ikona w zasobach pozostaje ciemna i pomaranczowa.
