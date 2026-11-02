"""Build check: every icon referenced by the EXE must match the intended ICO."""
from pathlib import Path
import hashlib
import struct
import sys
import pefile

exe=pefile.PE(str(Path(sys.argv[1])))
ico=Path(sys.argv[2]).read_bytes()
count=struct.unpack_from('<H',ico,4)[0]
expected=[]
for index in range(count):
    size,offset=struct.unpack_from('<II',ico,6+index*16+8)
    expected.append(hashlib.sha256(ico[offset:offset+size]).digest())
images={}
groups=[]
for resource in exe.DIRECTORY_ENTRY_RESOURCE.entries:
    if resource.id not in (3,14):continue
    for entry in resource.directory.entries:
        data=entry.directory.entries[0].data.struct
        blob=exe.get_data(data.OffsetToData,data.Size)
        if resource.id==3:images[entry.id]=blob
        else:groups.append(blob)
assert groups,'No embedded icon group'
for group in groups:
    actual=[]
    for index in range(struct.unpack_from('<H',group,4)[0]):
        resource_id=struct.unpack_from('<H',group,6+index*14+12)[0]
        actual.append(hashlib.sha256(images[resource_id]).digest())
    assert sorted(actual)==sorted(expected),'EXE icon differs from the intended dark ICO'
print(f'Embedded EXE icon verified against source: {count} sizes')
