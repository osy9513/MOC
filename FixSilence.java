import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class FixSilence {
    public static void main(String[] args) throws Exception {
        File dir = new File("d:/ai/MocPlugin/src/main/java/me/user/moc/ability/impl");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files == null)
            return;

        int count = 0;
        Pattern p = Pattern.compile("hasAbility\\(\\s*([a-zA-Z0-9_]+)\\s*,\\s*getCode\\(\\)\\s*\\)");

        for (File file : files) {
            String content = new String(Files.readAllBytes(file.toPath()), "UTF-8");
            if (content.contains("@EventHandler") && !content.contains("isSilenced")) {
                // Determine newline type (Windows \r\n vs Unix \n)
                String newline = content.contains("\r\n") ? "\r\n" : "\n";
                String[] lines = content.split("\\r?\\n", -1);
                boolean modified = false;
                StringBuilder result = new StringBuilder();

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    result.append(line);
                    if (i < lines.length - 1)
                        result.append(newline);

                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        String playerVar = m.group(1);
                        boolean hasReturn = line.contains("return");
                        if (!hasReturn && i + 1 < lines.length && lines[i + 1].contains("return")) {
                            hasReturn = true;
                        }
                        if (hasReturn) {
                            Matcher indentM = Pattern.compile("^(\\s*)").matcher(line);
                            String indent = indentM.find() ? indentM.group(1) : "";
                            result.append(indent).append("// [추가] 능력이 봉인된 상태 (침묵)인지 체크").append(newline);
                            result.append(indent).append("if (isSilenced(").append(playerVar).append(")) return;")
                                    .append(newline);
                            modified = true;
                        }
                    }
                }
                if (modified) {
                    Files.write(file.toPath(), result.toString().getBytes("UTF-8"));
                    count++;
                    System.out.println("Modified " + file.getName());
                }
            }
        }
        System.out.println("Total files modified successfully: " + count);
    }
}
