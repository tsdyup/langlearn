package dev.langlearn.client.speech;
import java.nio.file.*;
public class TtsTest {
  public static void main(String[] a) throws Exception {
    Tts.init(Path.of("ttsbase"));
    String[][] cases = {
      {"ru/ru_RU/irina/medium/ru_RU-irina-medium", "Незеритовый нагрудник"},
      {"ru/ru_RU/denis/medium/ru_RU-denis-medium", "Незеритовый нагрудник"},
      {"zh/zh_CN/huayan/medium/zh_CN-huayan-medium", "钻石剑"},
      {"zh/zh_CN/chaowen/medium/zh_CN-chaowen-medium", "钻石剑"},
      {"ru/ru_RU/irina/medium/ru_RU-irina-medium", "палка"},
      {"ru/ru_RU/denis/medium/ru_RU-denis-medium", "палка"},
      {"ru/ru_RU/irina/medium/ru_RU-irina-medium", "алмазный меч"},
      {"zh/zh_CN/huayan/medium/zh_CN-huayan-medium", "苹果"},
      {"zh/zh_CN/chaowen/medium/zh_CN-chaowen-medium", "苹果"},
    };
    for (String[] c : cases) {
      long t0 = System.currentTimeMillis();
      try { Path w = Tts.synthesize(c[0], c[1]); System.out.println("OK   " + c[0] + " " + Files.size(w) + " bytes in " + (System.currentTimeMillis()-t0) + " ms -> " + w); }
      catch (Exception e) { System.out.println("FAIL " + c[0] + ": " + e.getMessage().replaceAll("\\s+", " ").substring(0, Math.min(300, e.getMessage().length()))); }
    }
  }
}
