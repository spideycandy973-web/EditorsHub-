# Ghidra headless post-script (Jython). Export a native function inventory.
# Writes a marker line first so a script failure is distinguishable from an
# empty binary, then appends every discovered function.
from java.io import File
out = File('/tmp/ghidra-functions.txt')
f = open(out.getAbsolutePath(), 'w')
try:
    f.write('# program=%s\n' % currentProgram.getName())
    fm = currentProgram.getFunctionManager()
    n = 0
    for fn in fm.getFunctions(True):
        f.write('%s %s\n' % (fn.getEntryPoint(), fn.getName()))
        n += 1
    f.write('# functions=%d\n' % n)
finally:
    f.close()
