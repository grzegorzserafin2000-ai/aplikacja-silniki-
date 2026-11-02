# Kreator na tablet — ZIEHL-ABEGG 1.1.0

Ostateczny zakres potwierdzony przez użytkownika: tylko ZIEHL-ABEGG.
Nie przenosimy komend drugiej rodziny silników z referencyjnego APK.

Menu: Konfiguracja standardowa / Konfiguracja zaawansowana.
Standardowa: C/K i numer centrali, typ (nawiewno-wywiewna, nawiewna, wywiewna),
liczby silników 1–8 dla aktywnych sekcji, przygotowanie, lista kolejnych silników,
weryfikacja całej centrali i eksport raportu. Można wznowić zapisaną sesję.

Nawiew: 231,233,…245. Wywiew: 232,234,…246. Komunikacja 9600/8N2, CM=3.
W kreatorze cel jest przypisany do pozycji na liście. Kodowanie korzysta z tego
samego silnika protokołu i zabezpieczeń identyfikacji co konfiguracja zaawansowana.
Nie wysyła się zapisu automatycznie po znalezieniu urządzenia.

Niepotwierdzone lub przerwane kodowanie nie zalicza silnika. Wszystkie pozycje
muszą mieć potwierdzone zapisy przed testem grupowym. Test grupowy tylko odczytuje
kod produktu i1, h3 oraz h4 na każdym zaplanowanym adresie przy 9600/8N2.
Brak odpowiedzi lub rozbieżność oznacza niepotwierdzony test centrali.
Po ponownym uruchomieniu aplikacji należy powtórzyć końcowy test.

Referencyjny APK dostarczył układ kreatora, nie komendy dla ZIEHL. W szczególności
nie przeniesiono adresów 221/222 ani rejestrów drugiego producenta.
Testy automatyczne nie zastępują sprawdzenia na fizycznym silniku i tablecie.

Osobna instalacja: applicationId `pl.cg.ziehlconfig.wizard`. Nazwa, logo i ikona bez zmian. Poprzednie aplikacje pozostają zainstalowane.

1.1.1: wybór oznaczenia C/K przez RadioGroup (domyślnie C), osobna walidacja cyfr numeru i błąd przy polu. Testy obejmują C12345 i K12345.
