# Reads the word list embedded in a Vosk graph/Gr.fst (OpenFst binary with output symbol table).
import struct, sys
def read_str(f):
    n = struct.unpack("<i", f.read(4))[0]; return f.read(n).decode("utf-8", "replace")
def read_symtab(f):
    magic = struct.unpack("<i", f.read(4))[0]
    name = read_str(f); avail = struct.unpack("<q", f.read(8))[0]; size = struct.unpack("<q", f.read(8))[0]
    syms = []
    for _ in range(size):
        s = read_str(f); k = struct.unpack("<q", f.read(8))[0]; syms.append(s)
    return magic, name, syms
with open(sys.argv[1], "rb") as f:
    magic = struct.unpack("<i", f.read(4))[0]
    fsttype = read_str(f); arctype = read_str(f)
    version, flags = struct.unpack("<ii", f.read(8))
    props = struct.unpack("<Q", f.read(8))[0]
    start, nstates, narcs = struct.unpack("<qqq", f.read(24))
    print("magic", hex(magic), fsttype, arctype, "version", version, "flags", flags, "header ends at", f.tell())
    syms = None
    if flags & 1:
        m, n, syms = read_symtab(f); print("isymbols", n, len(syms), "symtab magic", hex(m))
    if flags & 2:
        m, n, syms = read_symtab(f); print("osymbols", n, len(syms), "symtab magic", hex(m))
    if syms:
        print("sample", syms[:5], syms[1000:1005])
        open(sys.argv[2], "w", encoding="utf-8").write("\n".join(syms))
