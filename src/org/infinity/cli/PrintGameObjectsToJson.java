// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import org.infinity.AppOption;
import org.infinity.datatype.AbstractBitmap;
import org.infinity.datatype.Flag;
import org.infinity.datatype.IsNumeric;
import org.infinity.datatype.IsReference;
import org.infinity.datatype.IsTextual;
import org.infinity.datatype.ResourceRef;
import org.infinity.datatype.ResourceBitmap;
import org.infinity.datatype.StringRef;
import org.infinity.gui.menu.BrowserMenuBar;
import org.infinity.resource.AbstractStruct;
import org.infinity.resource.Closeable;
import org.infinity.resource.Profile;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.key.ResourceEntry;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Reads game resources selected by regular expressions and writes their parsed fields as JSON.
 */
public final class PrintGameObjectsToJson implements CommandLineTool {
  @Override
  public void run(String fileName) throws Exception {
    JSONObject input = new JSONObject(new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8));
    Path workingDirectory = Paths.get(fileName).toAbsolutePath().getParent();
    if (workingDirectory == null) {
      workingDirectory = Paths.get("").toAbsolutePath();
    }

    Path gamePath = resolvePath(workingDirectory, input.optString("game", ""));
    Path weiduPath = resolvePath(workingDirectory, input.optString("weidu", ""));
    Path outputPath = resolvePath(workingDirectory, input.optString("output", ""));
    JSONArray resourcePatterns = input.optJSONArray("resources");
    if (!Files.isRegularFile(gamePath) || !"key".equalsIgnoreCase(extension(gamePath))) {
      throw new IOException("The 'game' path must point to a chitin.key file.");
    }
    if (!Files.isRegularFile(weiduPath)) {
      throw new IOException("The 'weidu' path must point to a WeiDU executable.");
    }
    if (!Files.isDirectory(outputPath)) {
      throw new IOException("The 'output' path must point to an existing empty folder.");
    }
    try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(outputPath)) {
      if (stream.iterator().hasNext()) {
        throw new IOException("The 'output' folder must be empty: " + outputPath);
      }
    }
    if (resourcePatterns == null || resourcePatterns.length() == 0) {
      throw new IOException("The 'resources' array must contain at least one regular expression.");
    }

    // Initialize AppOption before BrowserMenuBar to avoid the WeiDU default-value initialization cycle.
    AppOption.WEIDU_PATH.setValue(weiduPath.toString());
    if (!BrowserMenuBar.isInstantiated()) {
      new BrowserMenuBar();
    }
    if (!Profile.openGame(gamePath)) {
      throw new IOException("Unable to load game data: " + gamePath);
    }

    Pattern[] patterns = new Pattern[resourcePatterns.length()];
    for (int i = 0; i < patterns.length; i++) {
      String expression = resourcePatterns.optString(i, "");
      if (expression.trim().isEmpty()) {
        throw new IOException("Resource expression " + i + " must not be empty.");
      }
      patterns[i] = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
    }

    Collection<ResourceEntry> entries = ResourceFactory.getResourceTreeModel().getResourceEntries();
    for (ResourceEntry entry : entries) {
      if (!matches(entry.getResourceName(), patterns)) {
        continue;
      }
      Resource resource = ResourceFactory.getResource(entry);
      if (resource == null) {
        throw new IOException("Unable to parse resource: " + entry.getResourceName());
      }
      try {
        if (!(resource instanceof AbstractStruct)) {
          throw new IOException("Resource has no structured fields: " + entry.getResourceName());
        }
        Path outputFile = outputPath.resolve(entry.getResourceName() + ".json").normalize();
        if (!outputFile.getParent().equals(outputPath.toAbsolutePath().normalize())) {
          throw new IOException("Invalid resource filename: " + entry.getResourceName());
        }
        Files.write(outputFile, structToJson((AbstractStruct) resource).toString(2)
            .getBytes(StandardCharsets.UTF_8));
      } finally {
        if (resource instanceof Closeable) {
          ((Closeable) resource).close();
        }
      }
    }

  }

  private static boolean matches(String name, Pattern[] patterns) {
    for (Pattern pattern : patterns) {
      if (pattern.matcher(name).matches()) {
        return true;
      }
    }
    return false;
  }

  private static JSONObject structToJson(AbstractStruct struct) {
    JSONObject result = new JSONObject();
    for (StructEntry field : struct.getFields()) {
      putField(result, field.getName(), valueToJson(field));
    }
    return result;
  }

  private static void putField(JSONObject object, String name, Object value) {
    if (!object.has(name)) {
      object.put(name, value);
    } else {
      Object current = object.get(name);
      if (current instanceof JSONArray) {
        ((JSONArray) current).put(value);
      } else {
        object.put(name, new JSONArray().put(current).put(value));
      }
    }
  }

  private static Object valueToJson(StructEntry field) {
    if (field instanceof AbstractStruct) {
      return structToJson((AbstractStruct) field);
    }
    if (field instanceof ResourceRef) {
      return ((ResourceRef) field).getResourceName();
    }
    if (field instanceof ResourceBitmap) {
      ResourceBitmap bitmap = (ResourceBitmap) field;
      ResourceBitmap.RefEntry data = bitmap.getDataOf(bitmap.getLongValue());
      return data == null ? bitmap.getLongValue() : data.getResourceName();
    }
    if (field instanceof Flag) {
      return flagToJson((Flag) field);
    }
    if (field instanceof StringRef) {
      StringRef stringRef = (StringRef) field;
      return new JSONObject().put("value", stringRef.getLongValue()).put("description", stringRef.getText());
    }
    if (field instanceof AbstractBitmap) {
      AbstractBitmap<?> bitmap = (AbstractBitmap<?>) field;
      Object data = bitmap.getDataOf(bitmap.getLongValue());
      JSONObject result = new JSONObject().put("value", bitmap.getLongValue());
      if (data != null) {
        result.put("description", data.toString());
      }
      return result;
    }
    if (field instanceof IsNumeric) {
      return ((IsNumeric) field).getLongValue();
    }
    if (field instanceof IsTextual) {
      return ((IsTextual) field).getText();
    }
    if (field instanceof IsReference) {
      return field.toString();
    }
    return field.toString();
  }

  private static JSONObject flagToJson(Flag flag) {
    long value = flag.getLongValue();
    int digits = Math.max(1, flag.getSize() * 2);
    JSONObject result = new JSONObject().put("value", String.format(Locale.ROOT, "0x%0" + digits + "X", value));
    JSONArray bits = new JSONArray();
    for (int bit = 0; bit < flag.getSize() * 8; bit++) {
      if (flag.isFlagSet(bit)) {
        JSONObject item = new JSONObject().put("bit", bit + 1).put("description", flag.getString(bit))
            .put("value", 1L << bit);
        bits.put(item);
      }
    }
    return result.put("bits", bits);
  }

  private static Path resolvePath(Path workingDirectory, String value) throws IOException {
    if (value == null || value.trim().isEmpty()) {
      throw new IOException("A file path must not be empty.");
    }
    Path path = Paths.get(value);
    return path.isAbsolute() ? path : workingDirectory.resolve(path).normalize();
  }

  private static String extension(Path path) {
    String name = path.getFileName().toString();
    int index = name.lastIndexOf('.');
    return index < 0 ? "" : name.substring(index + 1);
  }
}
