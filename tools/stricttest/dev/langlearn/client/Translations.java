package dev.langlearn.client;
import java.util.*; import java.nio.file.*;
public class Translations { public static List<String> itemNames() { try { return new com.google.gson.Gson().fromJson(Files.readString(Path.of(System.getProperty("names"))), List.class); } catch (Exception e) { throw new RuntimeException(e); } } }
