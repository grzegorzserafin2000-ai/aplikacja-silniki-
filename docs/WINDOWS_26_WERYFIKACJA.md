# Clima Gold Windows 2.6 TEST

Źródło: Windows v2.5 z pakietu użytkownika. Przeniesiono logikę weryfikacji trybu i odczytów kontrolnych z Androida 0.16; rzeczywisty problem zapisu h3 nadal wymaga próby sprzętowej.

## Dostępne pliki
- [Przenośny EXE w ZIP, Windows x64](https://github.com/grzegorzserafin2000-ai/aplikacja-silniki-/releases/download/windows-v2.6.0-test/ClimaGold_ZIEHL_Windows_2_6_TEST.zip).
- [Pakiet źródłowy z instalatorem](../downloads/ClimaGold_Windows_2_6_TEST_Zrodla.zip).
- [Kompilacja Windows i weryfikacja EXE](https://github.com/grzegorzserafin2000-ai/aplikacja-silniki-/actions/runs/41406704695).

ZIP EXE: `ce209686a9c629542087ad92b02d88a409bb2dc31122a9339d933fe7372673e5` (SHA256). Pobrano z GitHub i porównano z sumą wydania. Plik EXE ma nagłówek Windows PE x64 (0x8664), nie jest plikiem Linux.

## Sprawdzono
- 61 testów w środowisku lokalnym z wirtualnym ekranem: wszystkie zakończone powodzeniem, bez pominiętych testów GUI.
- Workflow Windows: uruchomienie testów, kontrola GUI ze źródeł, zbudowanie PyInstaller i uruchomienie gotowego EXE.
- Smoke test EXE tworzy okno, ustawia skrót 231, wpisuje ręcznie 230 i sprawdza, że celem pozostaje 230. Potwierdza też obecność widocznego panelu edycji.
- Kontrola składni skryptu PowerShell w workflow.
- Symulowane scenariusze utraty ACK, opóźnionych zmian, aktywnego 231/zapisanego 230, rozbieżności E1/N2, przejścia 247/19200/8E1 na 232/9600/8N2, odrzucenia zapisu i nieznanego modelu.
- GUI rozróżnia aktywne i zapisane ustawienia, pokazuje firmware i model. Zmiana COM unieważnia poprzedni odczyt. Podczas zapisu pola są zablokowane.

## Ograniczenia
Brak testu z rzeczywistym adapterem USB-RS485 i silnikiem. Sam odczyt ACK nie oznacza potwierdzonej zmiany parametrów. Program nie rozszerza listy modeli dopuszczonych do zapisu. Logo pozostaje ze starego pakietu. EXE nie ma podpisu certyfikatem wydawcy.
