package dev.langlearn.client.speech;
import java.nio.file.Path;
import java.util.Set;
public class FstTest { public static void main(String[] a) throws Exception {
  Set<String> s = FstSymbols.read(Path.of(a[0]));
  System.out.println("java read " + (s == null ? "null" : s.size()) + " words");
  for (int i = 1; i < a.length; i++) System.out.println(a[i] + " -> " + (s != null && s.contains(a[i])));
}}
