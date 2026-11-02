# Logo użytkownika

Źródło: `Logo_ClimaGold_duże.zip`, pełny PNG 40363×12143. SHA256 oryginału: `b684bd61297d7ddc64900deb7764d71a64585ea2eddc58d37a6b4bef0382368a`.

`PrepareLogo.java` eksportuje zasoby do aplikacji: usuwa zewnętrzny biały margines, skaluje oryginalny rysunek do 1200×179 i przygotowuje ikonę z istniejącego pomarańczowego znaku. Nie odtwarza liter ani kształtów.

Eksport: `java -Xmx3g branding/PrepareLogo.java branding/logo-original.png /tmp/logo-export`. Pełny obraz służy nagłówkom; symbol z białym marginesem służy ikonom. ICO Windows wyeksportowano przez ImageMagick z małego PNG ikony: `magick climagold_mark.png -define icon:auto-resize=256,128,64,48,32,16 climagold.ico`.

Oryginał nie jest ładowany na tablecie ani w EXE, dzięki czemu ogromny rozmiar źródłowego obrazu nie obciąża uruchomienia aplikacji.

## Wygląd 1.0.1
Nagłówek eksportuje się poleceniem `java -Xmx3g branding/PrepareLogo.java branding/logo-original.png /tmp/logo-export "#263647"`. Eksport dopasowuje białe obszary do tła interfejsu, zachowując płaskie szare i pomarańczowe litery. Zasób nagłówka ma ten sam kolor tła co widok aplikacji. Wcześniejsze ikony Android i Windows zostały przywrócone z commita 37590bb; nie zastępuje się ich ikoną generowaną przez eksporter logo.
