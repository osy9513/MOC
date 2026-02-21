import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FindBug {
    public static void main(String[] args) throws Exception {
        Path dir = Paths.get("d:/ai/MocPlugin/src/main/java/me/user/moc/ability/impl");
        Files.walk(dir).filter(p -> p.toString().endsWith(".java")).forEach(p -> {
            try {
                String content = new String(Files.readAllBytes(p), "UTF-8");
                if (content.contains("PlayerInteractEvent") && content.contains("checkCooldown(")) {
                    boolean noRight = !content.contains("Action.RIGHT") && !content.contains("contains(\"RIGHT\")");
                    String noSpace = content.replaceAll("\\s+", "");
                    boolean noItemNull = !noSpace.contains("item==null") && !noSpace.contains("getItem()==null");
                    if (noRight || noItemNull) {
                        System.out.println("Suspect: " + p.getFileName() + " | NoRight: " + noRight + " | NoItemNull: "
                                + (noRight ? false : noItemNull));
                    }
                }
            } catch (Exception e) {
            }
        });
    }
}
