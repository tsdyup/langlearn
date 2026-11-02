package dev.langlearn.client.speech;
import java.io.*; import java.nio.file.*; import java.nio.charset.StandardCharsets;
public class WinTest {
  static String esc(String s){ StringBuilder b=new StringBuilder(); for(char c: s.toCharArray()) b.append(c<128?String.valueOf(c):String.format("\\u%04x",(int)c)); return b.toString(); }
  public static void main(String[] a) throws Exception {
    System.out.println("native.encoding=" + System.getProperty("native.encoding") + " jna default=" + com.sun.jna.Native.getDefaultStringEncoding());
    String grammar = "[\"палка\", \"книга\", \"яблоко\", \"алмазный меч\", \"камень\", \"[unk]\"]";
    // 1) the original org.vosk classes
    org.vosk.Model om = new org.vosk.Model(a[0]);
    // 2) our UTF-8 binding
    Vosk.Model m = new Vosk.Model(a[0]);
    for (File f : new File(a[1]).listFiles()) {
      byte[] all = Files.readAllBytes(f.toPath()); int off = 44;
      org.vosk.Recognizer orec = new org.vosk.Recognizer(om, 16000f, grammar);
      Vosk.Recognizer rec = new Vosk.Recognizer(m, 16000f, grammar);
      Vosk.Recognizer free = new Vosk.Recognizer(m, 16000f);
      for (int i = off; i < all.length; i += 3200) {
        int n = Math.min(3200, all.length - i); byte[] chunk = java.util.Arrays.copyOfRange(all, i, i + n);
        orec.acceptWaveForm(chunk, n); rec.acceptWaveForm(chunk, n); free.acceptWaveForm(chunk, n);
      }
      System.out.println(f.getName() + "  org.vosk=" + esc(orec.getFinalResult().replace("\n","")) + "  ours=" + esc(rec.getFinalResult().replace("\n","")) + "  ours-free=" + esc(free.getFinalResult().replace("\n","")));
    }
  }
}
