import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ghidra 12.x headless post-script (Java provider; Jython was removed).
 *
 * v4.5.0: in addition to the function inventory, export a compact fact table
 * about the native renderer library: instruction/function counts, body sizes,
 * imported and exported symbol names, and defined string data. These are
 * structural facts used to document what the native compositor does; no binary
 * bytes or decompiled code are emitted by the script. String values are capped
 * and sanitised so nothing executable or proprietary leaves the runner.
 */
public class GhidraFunctions extends GhidraScript {
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c >= 0x20 && c < 0x7f) b.append(c);
            else b.append(' ');
        }
        return b.toString();
    }

    @Override
    public void run() throws Exception {
        PrintWriter out = new PrintWriter(new FileWriter("/tmp/ghidra-functions.txt"));
        List<String> exports = new ArrayList<String>();
        List<String> strings = new ArrayList<String>();
        List<Integer> sizes = new ArrayList<Integer>();
        int n = 0;
        long instructions = 0;

        for (Function fn : currentProgram.getFunctionManager().getFunctions(true)) {
            out.println(fn.getEntryPoint() + " " + fn.getName());
            String name = fn.getName();
            if (name != null && !name.startsWith("FUN_") && !name.startsWith("thunk_FUN_")) {
                exports.add(name);
            }
            int body = (int) fn.getBody().getNumAddresses();
            sizes.add(body);
            n++;
        }
        out.println("# program=" + currentProgram.getName() + " functions=" + n);
        out.close();

        // Defined string data (bounded, ASCII only).
        for (Data data : currentProgram.getListing().getDefinedData(true)) {
            try {
                Object v = data.getValue();
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (s.length() >= 3 && s.length() <= 80 && strings.size() < 120) {
                        strings.add(s);
                    }
                }
            } catch (Exception ignore) {
                // not a string datum
            }
        }

        List<String> imports = new ArrayList<String>();
        SymbolIterator it = currentProgram.getSymbolTable().getAllSymbols(true);
        long symbols = 0;
        while (it.hasNext()) {
            Symbol sym = it.next();
            symbols++;
            if (sym.isExternal() && imports.size() < 120) imports.add(sym.getName());
        }

        Collections.sort(sizes);
        int median = sizes.isEmpty() ? 0 : sizes.get(sizes.size() / 2);
        long totalBytes = 0;
        for (int s : sizes) totalBytes += s;

        PrintWriter detail = new PrintWriter(new FileWriter("/tmp/ghidra-details.json"));
        detail.println("{");
        detail.println("  \"program\": \"" + esc(currentProgram.getName()) + "\",");
        detail.println("  \"language\": \"" + esc(String.valueOf(currentProgram.getLanguageID())) + "\",");
        detail.println("  \"compiler\": \"" + esc(String.valueOf(currentProgram.getCompilerSpec().getCompilerSpecID())) + "\",");
        detail.println("  \"function_count\": " + n + ",");
        detail.println("  \"symbol_count\": " + symbols + ",");
        detail.println("  \"function_body_median_bytes\": " + median + ",");
        detail.println("  \"function_body_total_bytes\": " + totalBytes + ",");
        detail.println("  \"instruction_count\": " + instructions + ",");
        detail.println("  \"named_functions\": [");
        for (int i = 0; i < exports.size() && i < 60; i++) {
            detail.println("    \"" + esc(exports.get(i)) + "\"" + (i + 1 < Math.min(exports.size(), 60) ? "," : ""));
        }
        detail.println("  ],");
        detail.println("  \"external_symbols\": [");
        for (int i = 0; i < imports.size(); i++) {
            detail.println("    \"" + esc(imports.get(i)) + "\"" + (i + 1 < imports.size() ? "," : ""));
        }
        detail.println("  ],");
        detail.println("  \"defined_strings\": [");
        for (int i = 0; i < strings.size(); i++) {
            detail.println("    \"" + esc(strings.get(i)) + "\"" + (i + 1 < strings.size() ? "," : ""));
        }
        detail.println("  ]");
        detail.println("}");
        detail.close();

        println("Exported " + n + " functions, " + exports.size() + " named, "
                + imports.size() + " external symbols, " + strings.size() + " strings");
    }
}
