import java.nio.file.*;

public class RemoveBOM {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("d:/ai/MocPlugin/src/main/java/me/user/moc/ability/impl/Ulquiorra.java");
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            byte[] noBom = new byte[bytes.length - 3];
            System.arraycopy(bytes, 3, noBom, 0, noBom.length);
            Files.write(path, noBom);
            System.out.println("BOM removed from Ulquiorra.java");
        } else {
            System.out.println("No BOM found.");
            // just write as UTF-8 string to normalize
            String content = new String(bytes, "UTF-8");
            content = content.replace("\uFEFF", "");
            Files.write(path, content.getBytes("UTF-8"));
            System.out.println("Stripped zero-width spaces just in case.");
        }
    }
}
