package dev.langlearn.client.speech;
import java.io.File; import java.nio.file.*; import java.util.*;
import dev.langlearn.client.text.*;
public class StrictTest {
  public static void main(String[] a) throws Exception {
    SpeechEngine.vocab = FstSymbols.read(Path.of(a[0], "graph", "Gr.fst"));
    SpeechEngine.vocab.removeIf(w -> w.isEmpty() || w.startsWith("<") || w.startsWith("#") || w.startsWith("!") || w.equals("[unk]"));
    Vosk.Model m = new Vosk.Model(a[0]);
    List<String> names = dev.langlearn.client.Translations.itemNames();
    for (String t : new String[]{"палка","книга","алмазный меч"}) System.out.println("confusables for " + t + ": " + Confusables.forWord("ru_ru", t.split(" ")[t.split(" ").length-1], SpeechEngine.vocab, 10));
    { List<String> sh = new ArrayList<>(names); Collections.shuffle(sh, new Random(7));
      String g0 = Grammar.assemble("ru_ru", "незеритовый нагрудник", sh, SpeechEngine.vocab, Grammar.Strictness.NORMAL);
      System.out.println("GRAMMAR " + g0);
      for (String n : names) if (n.toLowerCase().contains("незерит")) System.out.println("NAME " + n + " -> " + Grammar.phrase("ru_ru", n, SpeechEngine.vocab)); }
    // file name: <target>__<spoken>.wav
    for (File f : new File(a[1]).listFiles()) {
      String[] parts = f.getName().replace(".wav","").split("__");
      String target = parts[0].replace('_',' '), spoken = parts[1].replace('_',' ');
      for (Grammar.Strictness st : Grammar.Strictness.values()) {
        List<String> shuffled = new ArrayList<>(names); Collections.shuffle(shuffled, new Random(7));
        String g = Grammar.assemble("ru_ru", target, shuffled, SpeechEngine.vocab, st);
        Vosk.Recognizer r = new Vosk.Recognizer(m, 16000f, g); r.setWords(true);
        byte[] all = Files.readAllBytes(f.toPath());
        List<String> finals = new ArrayList<>();
        for (int i = 44; i < all.length; i += 3200) { int n = Math.min(3200, all.length - i); byte[] c = Arrays.copyOfRange(all, i, i + n); if (r.acceptWaveForm(c, n)) finals.add(r.getResult()); }
        finals.add(r.getFinalResult());
        StringBuilder sb = new StringBuilder();
        boolean pass = false;
        for (String js : finals) {
          var o = com.google.gson.JsonParser.parseString(js).getAsJsonObject();
          String text = o.get("text").getAsString(); if (text.isEmpty()) continue;
          double conf = -1; if (o.has("result")) { double s=0; int c=0; for (var el : o.getAsJsonArray("result")) { s += el.getAsJsonObject().get("conf").getAsDouble(); c++; } conf = s/c; }
          boolean ok = Matcher.score("ru_ru", target, text) >= 100 && (conf < 0 ? 1 : conf) >= st.minConfidence;
          pass |= ok;
          sb.append(text).append(" conf=").append(String.format("%.2f", conf)).append(ok ? " PASS" : "").append(" | ");
        }
        System.out.printf("target=%-14s said=%-14s %-6s phrases=%3d -> %s %s%n", target, spoken, st, g.split("\",").length, pass ? "ACCEPTED" : "rejected", sb);
      }
    }
  }
}
