"""Model IDs documented in ZIEHL-ABEGG L-BAL-E076-GB Index 013 p.18.
Read-only identification from FC04 i1 (address 1). ECblue h3 writes are
limited to tested protocol families; identity alone does not authorize writes.
"""
from typing import Optional
from protocol import ComSetting

PRODUCT_LABELS = {
    0x0032: 'ECblue Basic Standard (old)',
    0x0100: 'ECblue Basic Standard (new)',
    0x0101: 'ECblue MODBUS 02 (EC116)',
    0x0102: 'ECblue Lite (EC116)',
    0x0103: 'ECblue (EC90)',
    0x0200: 'Icontrol Basic',
    0x0201: 'Fcontrol Basic',
    0x0300: 'PMcontrol Basic',
    0x0401: 'AM-MODBUS',
    0x0402: 'AM-MODBUS-W',
}
# Strict allowlist used by the Android 0.8 app for this documented profile.
CONFIRMED_H3_PRODUCT_CODES = frozenset({0x0100, 0x0101})


def product_label(code: Optional[int]) -> str:
    if code is None:
        return 'Nie udało się odczytać kodu produktu (i1)'
    return f'{PRODUCT_LABELS.get(code, "Nieobsługiwany typ urządzenia")} [i1=0x{code:04X}]'


def h3_matches_active(raw_h3, saved: Optional[ComSetting], address: int,
                      baud: int, parity: str, stopbits: int) -> bool:
    if raw_h3 is None or saved is None:
        return False
    return (saved.address == address and saved.baud == baud and
            saved.mode[1] == parity and int(saved.mode[2]) == stopbits)


def pending_activation(detection) -> bool:
    """A verified known ECblue has h3 stored differently from its ACTIVE link.

    Read-only classification; never authorizes a write nor proves that a reset
    will succeed. The difference may result from an earlier write, stale
    registers, or device-specific behavior.
    """
    if detection is None:
        return False
    if detection.product_code not in CONFIRMED_H3_PRODUCT_CODES:
        return False
    if detection.saved is None or detection.raw_h3 is None:
        return False
    return not h3_matches_active(detection.raw_h3, detection.saved,
                                 detection.active_address, detection.baud,
                                 detection.parity, detection.stopbits)


def pending_message(detection) -> str:
    if not pending_activation(detection):
        return ''
    saved = detection.saved
    return (f'AKTYWNY adres/tryb: {detection.active_info}\n'
            f'ZAPISANY w h3: {saved.address} / {saved.baud} / {saved.mode} '
            f'(0x{detection.raw_h3:04X})\n\n'
            'Silnik jest rozpoznany, ale adres lub tryb zapisany w h3 różni się '
            'od bieżącego połączenia. Możliwa zmiana oczekująca na aktywację '
            'po restarcie. Sam odczyt nie dowodzi, że zmiana będzie aktywna.\n\n'
            'Nie zapisuj tych parametrów ponownie. Jeśli można bezpiecznie '
            'zatrzymać i odłączyć zasilanie silnika, wykonaj restart zgodnie '
            'z instrukcją, a następnie wyszukaj adres zapisany w h3. '
            'W razie rozbieżności sprawdź ustawienia w ZAset.')


def can_write(detection) -> bool:
    return (detection.product_code in CONFIRMED_H3_PRODUCT_CODES and
            h3_matches_active(detection.raw_h3, detection.saved,
                             detection.active_address, detection.baud,
                             detection.parity, detection.stopbits))


def reason_write_blocked(detection) -> str:
    if detection is None:
        return 'Najpierw wykryj silnik.'
    if detection.product_code is None:
        return 'Brak potwierdzonego identyfikatora urządzenia i1.'
    if detection.product_code not in CONFIRMED_H3_PRODUCT_CODES:
        return ('Ten model nie ma potwierdzonego profilu zapisu w tej wersji: '
                + product_label(detection.product_code) + '. Użyj ZAset.')
    if detection.saved is None:
        return 'Nie udało się zdekodować rejestru h3 dla tego modelu.'
    if pending_activation(detection):
        return pending_message(detection)
    return ''
